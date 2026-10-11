package com.rtoshield.routing;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.ActionType;
import com.rtoshield.domain.Journey;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The profit-aware core. There is only ONE copy of the money maths, and it lives here.
 *
 * Vocabulary (all money in rupees, all numbers from config):
 *   M = margin on the order = (selling price - cost price) x quantity
 *   R = loss if a parcel comes back = two-way shipping + packing + damage
 *   p = chance the order fails (from its risk level)
 *   r = share of would-be failures a journey stops, d = share of genuine customers a journey loses, c = its cost
 *
 * Expected profit of a journey:
 *   (1 - p) x (1 - d) x M   genuine customers who still buy
 *   - p x (1 - r) x R       failures that still get shipped and come back
 *   - c                     what the journey costs
 *
 * The (1 - d) part is what "protect genuine conversion" means: asking for more costs us customers.
 */
@Service
@RequiredArgsConstructor
public class ProfitCalculator {

    private final ConfigService config;

    /** One way of handling an order and the money it is expected to make. expectedProfit is NOT rounded. */
    public record JourneyOption(Journey journey, BigDecimal expectedProfit, BigDecimal actionCost) {
    }

    // ------------------------------------------------------------------ basic numbers

    /** Chance (0.0 to 1.0) that an order of this risk level fails. */
    public double pFail(RiskLevel risk) {
        return pFailDecimal(risk).doubleValue();
    }

    public BigDecimal pFailDecimal(RiskLevel risk) {
        return switch (risk) {
            case LOW -> config.getDecimal(ConfigKeys.PFAIL_LOW);
            case MEDIUM -> config.getDecimal(ConfigKeys.PFAIL_MEDIUM);
            case HIGH -> config.getDecimal(ConfigKeys.PFAIL_HIGH);
        };
    }

    /** R: what we lose in rupees when a parcel comes back. */
    public BigDecimal returnLoss() {
        return config.getDecimal(ConfigKeys.LOSS_SHIPPING_TWO_WAY)
                .add(config.getDecimal(ConfigKeys.LOSS_PACKING))
                .add(config.getDecimal(ConfigKeys.LOSS_DAMAGE));
    }

    /** M: the margin this order earns if it is delivered and paid. */
    public BigDecimal marginAmount(Order order) {
        return order.getProduct().getSellingPrice()
                .subtract(order.getProduct().getCostPrice())
                .multiply(BigDecimal.valueOf(order.getQuantity()));
    }

    /** What we expect to lose if we ship the order as it is: p x R, rounded to 2 decimals. */
    public BigDecimal baselineExpectedLoss(Order order, RiskLevel risk) {
        return returnLoss().multiply(pFailDecimal(risk)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Kept for compatibility: expected loss for an explicit failure chance. */
    public BigDecimal expectedLoss(Order order, double pFail) {
        return returnLoss().multiply(BigDecimal.valueOf(pFail)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Cost in rupees of one action (WhatsApp, IVR call, manual call). */
    public BigDecimal actionCost(ActionType type) {
        return switch (type) {
            case NONE -> BigDecimal.ZERO;
            case WHATSAPP -> config.getDecimal(ConfigKeys.COST_WHATSAPP);
            case IVR_CALL -> config.getDecimal(ConfigKeys.COST_IVR);
            case MANUAL_CALL -> config.getDecimal(ConfigKeys.COST_MANUAL);
        };
    }

    // ------------------------------------------------------------------ journeys

    /** c: what running this journey costs for this order. */
    public BigDecimal journeyCost(Journey journey, Order order) {
        BigDecimal whatsapp = config.getDecimal(ConfigKeys.COST_WHATSAPP);
        BigDecimal value = order.getOrderValue() == null ? BigDecimal.ZERO : order.getOrderValue();
        return switch (journey) {
            case FULL_COD, PREPAID_SHIP -> BigDecimal.ZERO;
            case OTP_CONFIRM, PARTIAL_COD, ADDRESS_VERIFY -> whatsapp;
            case PREPAID_INCENTIVE -> whatsapp.add(config.getDecimal(ConfigKeys.JOURNEY_PREPAID_DISCOUNT_RATE).multiply(value));
            case HUMAN_REVIEW -> config.getDecimal(ConfigKeys.COST_MANUAL);
        };
    }

    /**
     * Expected profit of one journey for one order. Only the five journeys the router can choose between have a model:
     * FULL_COD, OTP_CONFIRM, PARTIAL_COD, PREPAID_INCENTIVE, HUMAN_REVIEW.
     */
    public JourneyOption evaluate(Journey journey, Order order, RiskLevel risk) {
        BigDecimal p = pFailDecimal(risk);
        BigDecimal genuineWhoBuy = BigDecimal.ONE.subtract(p)
                .multiply(BigDecimal.ONE.subtract(dropoff(journey)))
                .multiply(marginAmount(order));
        BigDecimal failuresStillShipped = p
                .multiply(BigDecimal.ONE.subtract(failReduction(journey)))
                .multiply(returnLoss());
        BigDecimal cost = journeyCost(journey, order);
        return new JourneyOption(journey, genuineWhoBuy.subtract(failuresStillShipped).subtract(cost), cost);
    }

    private BigDecimal failReduction(Journey journey) {
        return switch (journey) {
            case FULL_COD -> BigDecimal.ZERO;
            case OTP_CONFIRM -> config.getDecimal(ConfigKeys.JOURNEY_OTP_FAIL_REDUCTION);
            case PARTIAL_COD -> config.getDecimal(ConfigKeys.JOURNEY_PARTIAL_FAIL_REDUCTION);
            case PREPAID_INCENTIVE -> config.getDecimal(ConfigKeys.JOURNEY_PREPAID_FAIL_REDUCTION);
            case HUMAN_REVIEW -> config.getDecimal(ConfigKeys.JOURNEY_HUMAN_FAIL_REDUCTION);
            default -> throw new IllegalArgumentException("No profit model for journey " + journey);
        };
    }

    private BigDecimal dropoff(Journey journey) {
        return switch (journey) {
            case FULL_COD -> BigDecimal.ZERO;
            case OTP_CONFIRM -> config.getDecimal(ConfigKeys.JOURNEY_OTP_DROPOFF);
            case PARTIAL_COD -> config.getDecimal(ConfigKeys.JOURNEY_PARTIAL_DROPOFF);
            case PREPAID_INCENTIVE -> config.getDecimal(ConfigKeys.DROPOFF_PREPAID_RATE);
            case HUMAN_REVIEW -> config.getDecimal(ConfigKeys.JOURNEY_HUMAN_DROPOFF);
            default -> throw new IllegalArgumentException("No profit model for journey " + journey);
        };
    }

    // ------------------------------------------------------------------ rescue (later)

    /**
     * STUB (M7, Person A). Returns the cheapest action that pays for itself after a failed delivery attempt.
     * TODO(A): decide when Person B starts M7 (rescue). Until then it always answers WHATSAPP.
     */
    public ActionType chooseRescueAction(Order order) {
        return ActionType.WHATSAPP;
    }
}
