package com.rtoshield.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tunable number lives here as a key with a default value.
 * DataSeeder copies these into the config_entries table on first start,
 * and ConfigService reads the database value first, then falls back to the default.
 * Rule: never hardcode a threshold, cost or point value in a service. Add a key here instead.
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    // trust points (keys match TrustEventType.configKey())
    public static final String TIER_PREPAID_ONLY_BELOW = "tier.prepaid_only_below";
    public static final String TIER_MANUAL_REVIEW_BELOW = "tier.manual_review_below";

    // cost of each action
    public static final String COST_WHATSAPP = "cost.action.whatsapp";
    public static final String COST_IVR = "cost.action.ivr";
    public static final String COST_MANUAL = "cost.action.manual";

    // loss if an order comes back (RTO)
    public static final String LOSS_SHIPPING_TWO_WAY = "loss.shipping_two_way";
    public static final String LOSS_PACKING = "loss.packing";
    public static final String LOSS_DAMAGE = "loss.damage";

    // chance of failure per risk level
    public static final String PFAIL_LOW = "pfail.low";
    public static final String PFAIL_MEDIUM = "pfail.medium";
    public static final String PFAIL_HIGH = "pfail.high";

    // score to risk level
    public static final String SCORE_HIGH_BELOW = "score.high_below";
    public static final String SCORE_MEDIUM_BELOW = "score.medium_below";

    // share of genuine customers who abandon when forced to pay online
    public static final String DROPOFF_PREPAID_RATE = "dropoff.prepaid_rate";

    // timers (minutes, so the demo can shrink them)
    public static final String CONFIRM_REMINDER_MINUTES = "confirm.reminder_minutes";
    public static final String DELAY_ALERT_MINUTES = "delay.alert_minutes";

    public static final String RESCUE_MAX_ATTEMPTS = "rescue.max_attempts";

    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("trust.points.delivered", "10");
        DEFAULTS.put("trust.points.paid_online", "5");
        DEFAULTS.put("trust.points.confirmed_quickly", "5");
        DEFAULTS.put("trust.points.refused", "-30");
        DEFAULTS.put("trust.points.unreachable", "-15");
        DEFAULTS.put("trust.points.late_cancel", "-20");
        DEFAULTS.put("trust.points.fake_address", "-25");

        DEFAULTS.put(TIER_PREPAID_ONLY_BELOW, "-40");
        DEFAULTS.put(TIER_MANUAL_REVIEW_BELOW, "-100");

        DEFAULTS.put(COST_WHATSAPP, "1");
        DEFAULTS.put(COST_IVR, "5");
        DEFAULTS.put(COST_MANUAL, "30");

        DEFAULTS.put(LOSS_SHIPPING_TWO_WAY, "150");
        DEFAULTS.put(LOSS_PACKING, "20");
        DEFAULTS.put(LOSS_DAMAGE, "30");

        DEFAULTS.put(PFAIL_LOW, "0.05");
        DEFAULTS.put(PFAIL_MEDIUM, "0.20");
        DEFAULTS.put(PFAIL_HIGH, "0.45");

        DEFAULTS.put(SCORE_HIGH_BELOW, "40");
        DEFAULTS.put(SCORE_MEDIUM_BELOW, "70");

        DEFAULTS.put(DROPOFF_PREPAID_RATE, "0.15");

        DEFAULTS.put(CONFIRM_REMINDER_MINUTES, "120");
        DEFAULTS.put(DELAY_ALERT_MINUTES, "2880");

        DEFAULTS.put(RESCUE_MAX_ATTEMPTS, "3");
    }
}
