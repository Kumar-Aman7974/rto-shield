package com.rtoshield.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tunable number lives here as a key with a default value.
 * DataSeeder copies new keys into the config_entries table on start-up (existing rows are kept),
 * and ConfigService reads the database value first, then falls back to the default.
 * Rule: never hardcode a threshold, cost or point value in a service. Add a key here instead.
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    // ---- trust points (keys match TrustEventType.configKey()) ----
    public static final String TIER_PREPAID_ONLY_BELOW = "tier.prepaid_only_below";
    public static final String TIER_MANUAL_REVIEW_BELOW = "tier.manual_review_below";

    // ---- cost of each action ----
    public static final String COST_WHATSAPP = "cost.action.whatsapp";
    public static final String COST_IVR = "cost.action.ivr";
    public static final String COST_MANUAL = "cost.action.manual";

    // ---- loss if an order comes back (RTO) ----
    public static final String LOSS_SHIPPING_TWO_WAY = "loss.shipping_two_way";
    public static final String LOSS_PACKING = "loss.packing";
    public static final String LOSS_DAMAGE = "loss.damage";

    // ---- chance of failure per risk level ----
    public static final String PFAIL_LOW = "pfail.low";
    public static final String PFAIL_MEDIUM = "pfail.medium";
    public static final String PFAIL_HIGH = "pfail.high";

    // ---- score to risk level ----
    public static final String SCORE_HIGH_BELOW = "score.high_below";
    public static final String SCORE_MEDIUM_BELOW = "score.medium_below";

    // ---- order-health score rules (M3): penalties are positive numbers that get subtracted from 100 ----
    public static final String ADDRESS_POOR_BELOW = "score.address_poor_below";
    public static final String ADDRESS_FAIR_BELOW = "score.address_fair_below";
    public static final String PEN_ADDRESS_POOR = "score.penalty.address_poor";
    public static final String PEN_ADDRESS_FAIR = "score.penalty.address_fair";

    public static final String PEN_COD = "score.penalty.cod";
    public static final String PEN_PARTIAL_COD = "score.penalty.partial_cod";

    public static final String HIGH_VALUE_MIN = "score.high_value_min";
    public static final String PEN_HIGH_VALUE_COD = "score.penalty.high_value_cod";

    public static final String PEN_TIER_CAUTION = "score.penalty.tier_caution";
    public static final String PEN_TIER_PREPAID_ONLY = "score.penalty.tier_prepaid_only";
    public static final String PEN_TIER_MANUAL_REVIEW = "score.penalty.tier_manual_review";
    public static final String PEN_FIRST_TIME = "score.penalty.first_time";
    public static final String TRUSTED_MIN_POINTS = "score.trusted_min_points";
    public static final String BONUS_TRUSTED = "score.bonus.trusted";

    public static final String PINCODE_HIGH_RATE = "score.pincode_high_rate";
    public static final String PINCODE_MEDIUM_RATE = "score.pincode_medium_rate";
    public static final String PEN_PINCODE_HIGH = "score.penalty.pincode_high";
    public static final String PEN_PINCODE_MEDIUM = "score.penalty.pincode_medium";
    public static final String PEN_PINCODE_UNKNOWN = "score.penalty.pincode_unknown";

    public static final String COURIER_MIN_SUCCESS = "score.courier_min_success";
    public static final String PEN_COURIER_WEAK = "score.penalty.courier_weak";

    public static final String PEN_CAMPAIGN_ADS = "score.penalty.campaign_ads";
    public static final String PEN_CAMPAIGN_INFLUENCER = "score.penalty.campaign_influencer";

    public static final String MARGIN_THIN_BELOW_PCT = "score.margin_thin_below_pct";
    public static final String PEN_MARGIN_THIN = "score.penalty.margin_thin";

    public static final String ODD_HOUR_START = "score.odd_hour_start";
    public static final String ODD_HOUR_END = "score.odd_hour_end";
    public static final String PEN_ODD_HOUR = "score.penalty.odd_hour";

    public static final String REPEAT_PHONE_MIN = "score.repeat_phone_min";
    public static final String PEN_REPEAT_PHONE = "score.penalty.repeat_phone";

    // ---- share of genuine customers who abandon when forced to pay online ----
    public static final String DROPOFF_PREPAID_RATE = "dropoff.prepaid_rate";

    // ---- journey economics (M4). These are ASSUMPTIONS: tune them with the merchant's real data. ----
    // fail_reduction = share of would-be failures the journey stops; dropoff = share of genuine customers lost to the extra step
    public static final String JOURNEY_OTP_FAIL_REDUCTION = "journey.otp_confirm.fail_reduction";
    public static final String JOURNEY_OTP_DROPOFF = "journey.otp_confirm.dropoff";
    public static final String JOURNEY_PARTIAL_FAIL_REDUCTION = "journey.partial_cod.fail_reduction";
    public static final String JOURNEY_PARTIAL_DROPOFF = "journey.partial_cod.dropoff";
    public static final String JOURNEY_PREPAID_FAIL_REDUCTION = "journey.prepaid_incentive.fail_reduction";
    public static final String JOURNEY_PREPAID_DISCOUNT_RATE = "journey.prepaid_incentive.discount_rate";
    public static final String JOURNEY_HUMAN_FAIL_REDUCTION = "journey.human_review.fail_reduction";
    public static final String JOURNEY_HUMAN_DROPOFF = "journey.human_review.dropoff";

    // ---- timers (minutes, so the demo can shrink them) ----
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

        DEFAULTS.put(ADDRESS_POOR_BELOW, "40");
        DEFAULTS.put(ADDRESS_FAIR_BELOW, "70");
        DEFAULTS.put(PEN_ADDRESS_POOR, "25");
        DEFAULTS.put(PEN_ADDRESS_FAIR, "10");

        DEFAULTS.put(PEN_COD, "15");
        DEFAULTS.put(PEN_PARTIAL_COD, "5");

        DEFAULTS.put(HIGH_VALUE_MIN, "2000");
        DEFAULTS.put(PEN_HIGH_VALUE_COD, "10");

        DEFAULTS.put(PEN_TIER_CAUTION, "12");
        DEFAULTS.put(PEN_TIER_PREPAID_ONLY, "30");
        DEFAULTS.put(PEN_TIER_MANUAL_REVIEW, "40");
        DEFAULTS.put(PEN_FIRST_TIME, "10");
        DEFAULTS.put(TRUSTED_MIN_POINTS, "20");
        DEFAULTS.put(BONUS_TRUSTED, "10");

        DEFAULTS.put(PINCODE_HIGH_RATE, "0.30");
        DEFAULTS.put(PINCODE_MEDIUM_RATE, "0.15");
        DEFAULTS.put(PEN_PINCODE_HIGH, "20");
        DEFAULTS.put(PEN_PINCODE_MEDIUM, "10");
        DEFAULTS.put(PEN_PINCODE_UNKNOWN, "8");

        DEFAULTS.put(COURIER_MIN_SUCCESS, "0.75");
        DEFAULTS.put(PEN_COURIER_WEAK, "5");

        DEFAULTS.put(PEN_CAMPAIGN_ADS, "8");
        DEFAULTS.put(PEN_CAMPAIGN_INFLUENCER, "5");

        DEFAULTS.put(MARGIN_THIN_BELOW_PCT, "25");
        DEFAULTS.put(PEN_MARGIN_THIN, "8");

        DEFAULTS.put(ODD_HOUR_START, "2");
        DEFAULTS.put(ODD_HOUR_END, "5");
        DEFAULTS.put(PEN_ODD_HOUR, "5");

        DEFAULTS.put(REPEAT_PHONE_MIN, "2");
        DEFAULTS.put(PEN_REPEAT_PHONE, "10");

        DEFAULTS.put(DROPOFF_PREPAID_RATE, "0.15");

        DEFAULTS.put(JOURNEY_OTP_FAIL_REDUCTION, "0.50");
        DEFAULTS.put(JOURNEY_OTP_DROPOFF, "0.05");
        DEFAULTS.put(JOURNEY_PARTIAL_FAIL_REDUCTION, "0.65");
        DEFAULTS.put(JOURNEY_PARTIAL_DROPOFF, "0.12");
        DEFAULTS.put(JOURNEY_PREPAID_FAIL_REDUCTION, "0.90");
        DEFAULTS.put(JOURNEY_PREPAID_DISCOUNT_RATE, "0.03");
        DEFAULTS.put(JOURNEY_HUMAN_FAIL_REDUCTION, "0.80");
        DEFAULTS.put(JOURNEY_HUMAN_DROPOFF, "0.10");

        DEFAULTS.put(CONFIRM_REMINDER_MINUTES, "120");
        DEFAULTS.put(DELAY_ALERT_MINUTES, "2880");

        DEFAULTS.put(RESCUE_MAX_ATTEMPTS, "3");
    }
}
