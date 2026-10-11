package com.rtoshield.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * What values each config key may hold. This protects the live demo: if a judge types "abc" or "1.5" into a key
 * that the code reads as a whole number, order creation would crash. Bad values are rejected before they are saved.
 *
 * Keys the code reads with getInt() MUST be INT here. Keys read with getDecimal()/getDouble() are DECIMAL.
 * Every key in ConfigKeys.DEFAULTS has a rule (a test checks this), so adding a key means adding its rule too.
 */
public final class ConfigRules {

    private ConfigRules() {
    }

    private enum Kind {INT, DECIMAL}

    private record Rule(Kind kind, BigDecimal min, BigDecimal max) {
    }

    private static final int MAX_TEXT_LENGTH = 20;
    private static final int MAX_DECIMAL_PLACES = 6;

    /** Pairs where the first key must never be above the second one. */
    private static final List<String[]> ORDERED_PAIRS = List.of(
            new String[]{ConfigKeys.TIER_MANUAL_REVIEW_BELOW, ConfigKeys.TIER_PREPAID_ONLY_BELOW},
            new String[]{ConfigKeys.SCORE_HIGH_BELOW, ConfigKeys.SCORE_MEDIUM_BELOW},
            new String[]{ConfigKeys.ADDRESS_POOR_BELOW, ConfigKeys.ADDRESS_FAIR_BELOW},
            new String[]{ConfigKeys.PINCODE_MEDIUM_RATE, ConfigKeys.PINCODE_HIGH_RATE},
            new String[]{ConfigKeys.ODD_HOUR_START, ConfigKeys.ODD_HOUR_END});

    /**
     * Checks a new value and returns it in tidy form (for example "0.50" becomes "0.5" and "007" becomes "7").
     *
     * @param currentValue looks up the current value of another key, used for the ordering rules
     * @throws IllegalArgumentException with a plain-words message if the value is not allowed
     */
    public static String validate(String key, String raw, Function<String, String> currentValue) {
        Rule rule = ruleFor(key);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown config key: " + key);
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(key + ": a value is required");
        }
        String text = raw.trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw bad(key, rule, raw);
        }

        BigDecimal number;
        if (rule.kind() == Kind.INT) {
            if (!text.matches("-?\\d{1,9}")) {
                throw bad(key, rule, raw);
            }
            number = new BigDecimal(text);
        } else {
            try {
                number = new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw bad(key, rule, raw);
            }
            if (number.stripTrailingZeros().scale() > MAX_DECIMAL_PLACES) {
                throw bad(key, rule, raw);
            }
        }
        if (number.compareTo(rule.min()) < 0 || number.compareTo(rule.max()) > 0) {
            throw bad(key, rule, raw);
        }
        checkOrder(key, number, currentValue);
        return plain(number);
    }

    /** Plain-words description of the allowed values, for the config API. */
    public static String describe(String key) {
        Rule rule = ruleFor(key);
        return rule == null ? "unknown key" : describe(rule);
    }

    // ------------------------------------------------------------------ rules per key

    private static Rule ruleFor(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith("trust.points.")) {
            return ints(-1000, 1000);
        }
        if (key.equals(ConfigKeys.TIER_PREPAID_ONLY_BELOW) || key.equals(ConfigKeys.TIER_MANUAL_REVIEW_BELOW)) {
            return ints(-10000, 0);
        }
        if (key.equals(ConfigKeys.SCORE_HIGH_BELOW) || key.equals(ConfigKeys.SCORE_MEDIUM_BELOW)
                || key.equals(ConfigKeys.ADDRESS_POOR_BELOW) || key.equals(ConfigKeys.ADDRESS_FAIR_BELOW)) {
            return ints(0, 100);
        }
        if (key.startsWith("score.penalty.") || key.equals(ConfigKeys.BONUS_TRUSTED)) {
            return ints(0, 100);
        }
        if (key.equals(ConfigKeys.TRUSTED_MIN_POINTS)) {
            return ints(0, 10000);
        }
        if (key.equals(ConfigKeys.ODD_HOUR_START) || key.equals(ConfigKeys.ODD_HOUR_END)) {
            return ints(0, 24);
        }
        if (key.equals(ConfigKeys.REPEAT_PHONE_MIN)) {
            return ints(1, 1000);
        }
        if (key.equals(ConfigKeys.HIGH_VALUE_MIN)) {
            return decimals(0, 10_000_000);
        }
        if (key.equals(ConfigKeys.MARGIN_THIN_BELOW_PCT)) {
            return decimals(0, 100);
        }
        if (key.equals(ConfigKeys.PINCODE_HIGH_RATE) || key.equals(ConfigKeys.PINCODE_MEDIUM_RATE)
                || key.equals(ConfigKeys.COURIER_MIN_SUCCESS)) {
            return decimals(0, 1);
        }
        if (key.startsWith("cost.action.")) {
            return decimals(0, 100_000);
        }
        if (key.startsWith("loss.")) {
            return decimals(0, 1_000_000);
        }
        if (key.startsWith("pfail.") || key.startsWith("dropoff.") || key.startsWith("journey.")) {
            return decimals(0, 1);
        }
        if (key.equals(ConfigKeys.CONFIRM_REMINDER_MINUTES) || key.equals(ConfigKeys.DELAY_ALERT_MINUTES)) {
            return ints(1, 100_000);
        }
        if (key.equals(ConfigKeys.RESCUE_MAX_ATTEMPTS)) {
            return ints(1, 10);
        }
        return null;
    }

    private static Rule ints(long min, long max) {
        return new Rule(Kind.INT, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    private static Rule decimals(long min, long max) {
        return new Rule(Kind.DECIMAL, BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    // ------------------------------------------------------------------ helpers

    private static void checkOrder(String key, BigDecimal value, Function<String, String> currentValue) {
        for (String[] pair : ORDERED_PAIRS) {
            String lower = pair[0];
            String upper = pair[1];
            if (key.equals(lower)) {
                BigDecimal other = new BigDecimal(currentValue.apply(upper));
                if (value.compareTo(other) > 0) {
                    throw new IllegalArgumentException(key + " (" + plain(value) + ") must not be above "
                            + upper + " (" + plain(other) + "). Change " + upper + " first.");
                }
            } else if (key.equals(upper)) {
                BigDecimal other = new BigDecimal(currentValue.apply(lower));
                if (value.compareTo(other) < 0) {
                    throw new IllegalArgumentException(key + " (" + plain(value) + ") must not be below "
                            + lower + " (" + plain(other) + "). Change " + lower + " first.");
                }
            }
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String describe(Rule rule) {
        return (rule.kind() == Kind.INT ? "a whole number" : "a number")
                + " from " + rule.min().toPlainString() + " to " + rule.max().toPlainString();
    }

    private static IllegalArgumentException bad(String key, Rule rule, String raw) {
        String shown = raw.length() > MAX_TEXT_LENGTH ? raw.substring(0, MAX_TEXT_LENGTH) + "..." : raw;
        return new IllegalArgumentException(key + " must be " + describe(rule) + " (got '" + shown + "')");
    }
}
