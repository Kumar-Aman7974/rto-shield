package com.rtoshield.scoring;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.*;
import com.rtoshield.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * M3: the order-health score.
 *
 * Start at 100 (a perfectly healthy order). Every rule that fires takes points off (or, for a trusted customer,
 * adds a few) and writes one plain-words reason that ends with its points, e.g. "Cash on delivery (-15)".
 * The reasons always add up to the score. The result is clamped to 0..100.
 * Risk level: score below score.high_below = HIGH, below score.medium_below = MEDIUM, otherwise LOW.
 *
 * Every number comes from config (see ConfigKeys), so thresholds can change without a redeploy.
 * Rules always run in the same order, so the reasons always appear in the same order.
 */
@Service
@RequiredArgsConstructor
public class RuleBasedHealthScorer implements OrderHealthScorer {

    private final ConfigService config;
    private final PincodeStatsService pincodeStats;
    private final OrderRepository orders;

    @Override
    public ScoreResult score(Order order) {
        Tally tally = new Tally();
        addressRule(order, tally);
        paymentRule(order, tally);
        highValueRule(order, tally);
        historyRules(order, tally);
        pincodeRule(order, tally);
        courierRule(order, tally);
        campaignRule(order, tally);
        marginRule(order, tally);
        oddHourRule(order, tally);
        repeatPhoneRule(order, tally);

        int score = Math.max(0, Math.min(100, 100 + tally.total));
        List<String> reasons = tally.reasons.isEmpty()
                ? List.of("No risk factors found")
                : List.copyOf(tally.reasons);
        return new ScoreResult(score, riskFor(score), reasons);
    }

    // ------------------------------------------------------------------ rules

    /** Incomplete addresses are the top cause of failed deliveries. */
    private void addressRule(Order order, Tally tally) {
        int quality = order.getAddressQuality();
        if (quality < config.getInt(ConfigKeys.ADDRESS_POOR_BELOW)) {
            tally.add(-penalty(ConfigKeys.PEN_ADDRESS_POOR), "Address looks incomplete: quality " + quality + "/100");
        } else if (quality < config.getInt(ConfigKeys.ADDRESS_FAIR_BELOW)) {
            tally.add(-penalty(ConfigKeys.PEN_ADDRESS_FAIR), "Address only partly complete: quality " + quality + "/100");
        }
    }

    /** Cash on delivery is where low-intent orders hide. Prepaid orders get no penalty. */
    private void paymentRule(Order order, Tally tally) {
        PaymentType type = order.getPaymentType();
        if (type == PaymentType.COD) {
            tally.add(-penalty(ConfigKeys.PEN_COD), "Cash on delivery");
        } else if (type == PaymentType.PARTIAL_COD) {
            tally.add(-penalty(ConfigKeys.PEN_PARTIAL_COD), "Partial cash on delivery");
        }
    }

    /** A big cash order costs more if it fails. */
    private void highValueRule(Order order, Tally tally) {
        BigDecimal value = order.getOrderValue();
        if (order.getPaymentType() == PaymentType.COD && value != null
                && value.compareTo(config.getDecimal(ConfigKeys.HIGH_VALUE_MIN)) >= 0) {
            tally.add(-penalty(ConfigKeys.PEN_HIGH_VALUE_COD),
                    "High-value cash order: Rs " + value.setScale(0, RoundingMode.HALF_UP).toPlainString());
        }
    }

    /** Customer history: trust tier, trusted bonus, first-time customer. */
    private void historyRules(Order order, Tally tally) {
        Customer customer = order.getCustomer();
        if (customer == null) {
            return;
        }
        CustomerTier tier = customer.getTier() == null ? CustomerTier.GOOD : customer.getTier();
        int balance = customer.getTrustBalance();
        switch (tier) {
            case MANUAL_REVIEW -> tally.add(-penalty(ConfigKeys.PEN_TIER_MANUAL_REVIEW),
                    "Customer is under manual review, trust balance is " + balance);
            case PREPAID_ONLY -> tally.add(-penalty(ConfigKeys.PEN_TIER_PREPAID_ONLY),
                    "Customer must prepay, trust balance is " + balance);
            case CAUTION -> tally.add(-penalty(ConfigKeys.PEN_TIER_CAUTION),
                    "Customer has some bad history, trust balance is " + balance);
            case GOOD -> {
                if (balance >= config.getInt(ConfigKeys.TRUSTED_MIN_POINTS)) {
                    tally.add(config.getInt(ConfigKeys.BONUS_TRUSTED),
                            "Trusted customer, trust balance is " + balance);
                }
            }
        }
        // totalOrders is counted AFTER scoring (see OrderService), so a first order still shows 0 here
        if (customer.getTotalOrders() == 0) {
            tally.add(-penalty(ConfigKeys.PEN_FIRST_TIME), "First-time customer, no delivery history");
        }
    }

    /** Pincode behaviour: how often deliveries fail there, across all couriers. */
    private void pincodeRule(Order order, Tally tally) {
        String pincode = order.getPincode();
        if (pincode == null) {
            return;
        }
        if (!pincodeStats.hasHistory(pincode)) {
            tally.add(-penalty(ConfigKeys.PEN_PINCODE_UNKNOWN), "No delivery history for pincode " + pincode);
            return;
        }
        double rate = pincodeStats.refusalRate(pincode);
        if (rate >= config.getDouble(ConfigKeys.PINCODE_HIGH_RATE)) {
            tally.add(-penalty(ConfigKeys.PEN_PINCODE_HIGH),
                    "High-risk pincode " + pincode + ": " + percent(rate) + " of deliveries fail");
        } else if (rate >= config.getDouble(ConfigKeys.PINCODE_MEDIUM_RATE)) {
            tally.add(-penalty(ConfigKeys.PEN_PINCODE_MEDIUM),
                    "Moderate-risk pincode " + pincode + ": " + percent(rate) + " of deliveries fail");
        }
    }

    /** Courier performance: fires only if even the BEST courier is weak here (so it is not the same signal as the pincode rule). */
    private void courierRule(Order order, Tally tally) {
        String pincode = order.getPincode();
        if (pincode == null || !pincodeStats.hasHistory(pincode)) {
            return;
        }
        double best = pincodeStats.bestCourierSuccessRate(pincode);
        if (best < config.getDouble(ConfigKeys.COURIER_MIN_SUCCESS)) {
            tally.add(-penalty(ConfigKeys.PEN_COURIER_WEAK),
                    "Even the best courier here delivers only " + percent(best) + " of parcels");
        }
    }

    /** Orders from ads and influencers are more impulsive. */
    private void campaignRule(Order order, Tally tally) {
        CampaignSource source = order.getCampaignSource();
        if (source == CampaignSource.ADS) {
            tally.add(-penalty(ConfigKeys.PEN_CAMPAIGN_ADS), "Order came from an ad campaign");
        } else if (source == CampaignSource.INFLUENCER) {
            tally.add(-penalty(ConfigKeys.PEN_CAMPAIGN_INFLUENCER), "Order came from an influencer campaign");
        }
    }

    /** Thin margin: a single failed delivery wipes out the profit. */
    private void marginRule(Order order, Tally tally) {
        Product product = order.getProduct();
        if (product == null) {
            return;
        }
        BigDecimal margin = product.getMarginPct();
        if (margin.compareTo(config.getDecimal(ConfigKeys.MARGIN_THIN_BELOW_PCT)) < 0) {
            tally.add(-penalty(ConfigKeys.PEN_MARGIN_THIN),
                    "Thin margin " + margin.toPlainString() + "%: a failed delivery wipes out the profit");
        }
    }

    /** Orders placed in the small hours (default 02:00 to 04:59) are riskier. */
    private void oddHourRule(Order order, Tally tally) {
        LocalDateTime when = order.getCreatedAt();
        if (when == null) {
            return;
        }
        int hour = when.getHour();
        if (hour >= config.getInt(ConfigKeys.ODD_HOUR_START) && hour < config.getInt(ConfigKeys.ODD_HOUR_END)) {
            tally.add(-penalty(ConfigKeys.PEN_ODD_HOUR),
                    "Ordered at an odd hour (" + String.format("%02d:%02d", hour, when.getMinute()) + ")");
        }
    }

    /** Many orders from one phone in 24 hours. The order being scored is already saved, so it counts itself once. */
    private void repeatPhoneRule(Order order, Tally tally) {
        if (order.getPhone() == null || order.getCreatedAt() == null) {
            return;
        }
        long inWindow = orders.countByPhoneAndCreatedAtAfter(order.getPhone(), order.getCreatedAt().minusHours(24));
        long others = Math.max(0, inWindow - 1);
        if (others >= config.getInt(ConfigKeys.REPEAT_PHONE_MIN)) {
            tally.add(-penalty(ConfigKeys.PEN_REPEAT_PHONE),
                    "Same phone placed " + others + " other orders in 24 hours");
        }
    }

    // ------------------------------------------------------------------ helpers

    private RiskLevel riskFor(int score) {
        if (score < config.getInt(ConfigKeys.SCORE_HIGH_BELOW)) {
            return RiskLevel.HIGH;
        }
        if (score < config.getInt(ConfigKeys.SCORE_MEDIUM_BELOW)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private int penalty(String key) {
        return config.getInt(key);
    }

    private static String percent(double rate) {
        return Math.round(rate * 100) + "%";
    }

    /** Running total of points plus the reasons, in the order the rules fired. */
    private static final class Tally {
        private int total = 0;
        private final List<String> reasons = new ArrayList<>();

        void add(int delta, String text) {
            total += delta;
            reasons.add(text + " (" + (delta > 0 ? "+" : "") + delta + ")");
        }
    }
}
