package com.rtoshield.routing;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.CustomerTier;
import com.rtoshield.domain.DecisionOutcome;
import com.rtoshield.domain.Journey;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.PaymentType;
import com.rtoshield.domain.RiskLevel;
import com.rtoshield.domain.ScoreResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * M4: the brain. Decides what to do with a new order, in this fixed order of rules (first match wins):
 *
 *  1. Cash order from a customer under MANUAL_REVIEW        -> HUMAN_REVIEW
 *  2. Address quality below the "poor" limit                -> ADDRESS_VERIFY (even if prepaid: a bad address fails either way)
 *  3. Prepaid order                                         -> PREPAID_SHIP
 *  4. Customer already chose partial COD                    -> PARTIAL_COD
 *  5. COD from a PREPAID_ONLY customer                      -> PREPAID_INCENTIVE (they must pay online)
 *  6. Everything else (plain COD)                           -> the journey with the best expected profit
 *                                                              (FULL_COD, OTP_CONFIRM, PARTIAL_COD, PREPAID_INCENTIVE, HUMAN_REVIEW).
 *                                                              A first-time customer can never get FULL_COD.
 *                                                              Ties go to the journey with less friction.
 *
 * The reason text is written for humans: it is what the judges see.
 */
@Service
@RequiredArgsConstructor
public class JourneyRouter {

    /** Journeys the profit model chooses between, from least to most friction (this order breaks ties). */
    private static final List<Journey> PROFIT_CHOICES = List.of(
            Journey.FULL_COD, Journey.OTP_CONFIRM, Journey.PARTIAL_COD, Journey.PREPAID_INCENTIVE, Journey.HUMAN_REVIEW);

    /** Journeys after which the parcel is shipped, so a courier is recommended right away. */
    private static final Set<Journey> SHIPS_AFTERWARDS = EnumSet.of(
            Journey.FULL_COD, Journey.PREPAID_SHIP, Journey.OTP_CONFIRM, Journey.PARTIAL_COD, Journey.PREPAID_INCENTIVE);

    private final ConfigService config;
    private final ProfitCalculator profit;
    private final CourierRecommender courierRecommender;

    public DecisionOutcome decide(Order order, ScoreResult score) {
        RiskLevel risk = score.riskLevel();
        PaymentType payment = order.getPaymentType() == null ? PaymentType.COD : order.getPaymentType();
        boolean cash = payment != PaymentType.PREPAID;
        Customer customer = order.getCustomer();
        CustomerTier tier = (customer == null || customer.getTier() == null) ? CustomerTier.GOOD : customer.getTier();
        int balance = customer == null ? 0 : customer.getTrustBalance();
        boolean firstTime = customer != null && customer.getTotalOrders() == 0;

        if (cash && tier == CustomerTier.MANUAL_REVIEW) {
            return outcome(Journey.HUMAN_REVIEW, order, risk,
                    "Customer is under manual review (trust balance " + balance + "): a person must approve before shipping.");
        }
        int quality = order.getAddressQuality();
        if (quality < config.getInt(ConfigKeys.ADDRESS_POOR_BELOW)) {
            return outcome(Journey.ADDRESS_VERIFY, order, risk,
                    "Address quality is only " + quality + "/100: confirm the address with the customer before shipping.");
        }
        if (payment == PaymentType.PREPAID) {
            return outcome(Journey.PREPAID_SHIP, order, risk,
                    "Prepaid order: no cash-on-delivery risk, ship directly.");
        }
        if (payment == PaymentType.PARTIAL_COD) {
            return outcome(Journey.PARTIAL_COD, order, risk,
                    "Customer chose partial cash on delivery: collect the advance, then ship.");
        }
        if (tier == CustomerTier.PREPAID_ONLY) {
            return outcome(Journey.PREPAID_INCENTIVE, order, risk,
                    "Customer must prepay (trust balance " + balance + "): ask for online payment with a small discount.");
        }
        return chooseByProfit(order, risk, firstTime);
    }

    private DecisionOutcome chooseByProfit(Order order, RiskLevel risk, boolean firstTime) {
        List<ProfitCalculator.JourneyOption> options = new ArrayList<>();
        for (Journey journey : PROFIT_CHOICES) {
            if (firstTime && journey == Journey.FULL_COD) {
                continue;
            }
            options.add(profit.evaluate(journey, order, risk));
        }

        ProfitCalculator.JourneyOption best = options.get(0);
        for (ProfitCalculator.JourneyOption option : options) {
            if (option.expectedProfit().compareTo(best.expectedProfit()) > 0) {
                best = option;
            }
        }
        ProfitCalculator.JourneyOption nextBest = null;
        for (ProfitCalculator.JourneyOption option : options) {
            if (option == best) {
                continue;
            }
            if (nextBest == null || option.expectedProfit().compareTo(nextBest.expectedProfit()) > 0) {
                nextBest = option;
            }
        }

        StringBuilder reason = new StringBuilder();
        if (firstTime) {
            reason.append("First-time customer, so shipping without confirmation is not allowed. ");
        }
        reason.append("Best expected profit: ").append(best.journey())
                .append(" Rs ").append(money(best.expectedProfit()))
                .append(" (next best: ").append(nextBest.journey())
                .append(" Rs ").append(money(nextBest.expectedProfit())).append("). ")
                .append("Risk ").append(risk).append(": ")
                .append(percent(profit.pFailDecimal(risk))).append("% chance of failure, margin Rs ")
                .append(money(profit.marginAmount(order))).append(", loss if returned Rs ")
                .append(money(profit.returnLoss())).append(".");
        return outcome(best.journey(), order, risk, reason.toString());
    }

    /** Builds the final answer: journey, courier (only if the parcel ships afterwards), costs and the reason. */
    private DecisionOutcome outcome(Journey journey, Order order, RiskLevel risk, String reason) {
        BigDecimal expectedLoss = profit.baselineExpectedLoss(order, risk);
        BigDecimal actionCost = profit.journeyCost(journey, order).setScale(2, RoundingMode.HALF_UP);
        Long courierId = null;
        String fullReason = reason;
        if (SHIPS_AFTERWARDS.contains(journey)) {
            Optional<CourierRecommender.CourierChoice> choice = courierRecommender.recommend(order);
            if (choice.isPresent()) {
                courierId = choice.get().courier().getId();
                fullReason = reason + " Courier: " + choice.get().courier().getName()
                        + " (expected cost Rs " + money(choice.get().expectedCost()) + ").";
            }
        }
        return new DecisionOutcome(journey, courierId, expectedLoss, actionCost, fullReason);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 0.45 becomes "45", 0.05 becomes "5". */
    private static String percent(BigDecimal probability) {
        return probability.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
    }
}
