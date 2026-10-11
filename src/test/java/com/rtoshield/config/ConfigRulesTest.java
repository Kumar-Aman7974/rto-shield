package com.rtoshield.config;

import com.rtoshield.domain.TrustEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which values each setting accepts. A bad value here would crash order creation during the demo. */
class ConfigRulesTest {

    private static final Function<String, String> DEFAULTS = ConfigKeys.DEFAULTS::get;

    @Test
    void everyDefaultKeyHasARuleAndItsDefaultIsValid() {
        for (Map.Entry<String, String> entry : ConfigKeys.DEFAULTS.entrySet()) {
            assertThat(ConfigRules.validate(entry.getKey(), entry.getValue(), DEFAULTS))
                    .as("default of " + entry.getKey())
                    .isNotNull();
            assertThat(ConfigRules.describe(entry.getKey())).as("rule text of " + entry.getKey()).isNotEqualTo("unknown key");
        }
    }

    @Test
    void keysTheCodeReadsAsWholeNumbersRefuseDecimals() {
        // This list was built by scanning the code for getInt(...) and penalty(...) calls.
        String[] wholeNumberKeys = {ConfigKeys.ADDRESS_FAIR_BELOW, ConfigKeys.ADDRESS_POOR_BELOW, ConfigKeys.BONUS_TRUSTED, ConfigKeys.ODD_HOUR_END, ConfigKeys.ODD_HOUR_START, ConfigKeys.PEN_ADDRESS_FAIR, ConfigKeys.PEN_ADDRESS_POOR, ConfigKeys.PEN_CAMPAIGN_ADS, ConfigKeys.PEN_CAMPAIGN_INFLUENCER, ConfigKeys.PEN_COD, ConfigKeys.PEN_COURIER_WEAK, ConfigKeys.PEN_FIRST_TIME, ConfigKeys.PEN_HIGH_VALUE_COD, ConfigKeys.PEN_MARGIN_THIN, ConfigKeys.PEN_ODD_HOUR, ConfigKeys.PEN_PARTIAL_COD, ConfigKeys.PEN_PINCODE_HIGH, ConfigKeys.PEN_PINCODE_MEDIUM, ConfigKeys.PEN_PINCODE_UNKNOWN, ConfigKeys.PEN_REPEAT_PHONE, ConfigKeys.PEN_TIER_CAUTION, ConfigKeys.PEN_TIER_MANUAL_REVIEW, ConfigKeys.PEN_TIER_PREPAID_ONLY, ConfigKeys.REPEAT_PHONE_MIN, ConfigKeys.SCORE_HIGH_BELOW, ConfigKeys.SCORE_MEDIUM_BELOW, ConfigKeys.TIER_MANUAL_REVIEW_BELOW, ConfigKeys.TIER_PREPAID_ONLY_BELOW, ConfigKeys.TRUSTED_MIN_POINTS};
        for (String key : wholeNumberKeys) {
            assertThatThrownBy(() -> ConfigRules.validate(key, "1.5", DEFAULTS))
                    .as(key + " must refuse 1.5")
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (TrustEventType type : TrustEventType.values()) {
            assertThatThrownBy(() -> ConfigRules.validate(type.configKey(), "1.5", DEFAULTS))
                    .as(type.configKey() + " must refuse 1.5")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "score.penalty.cod,0,true",
            "score.penalty.cod,100,true",
            "score.penalty.cod,101,false",
            "score.penalty.cod,-1,false",
            "pfail.high,0,true",
            "pfail.high,1,true",
            "pfail.high,1.01,false",
            "pfail.high,-0.1,false",
            "tier.prepaid_only_below,0,true",
            "tier.prepaid_only_below,1,false",
            "tier.prepaid_only_below,-100,true",
            "tier.prepaid_only_below,-101,false",
            "trust.points.refused,-1000,true",
            "trust.points.refused,-1001,false",
            "trust.points.delivered,1000,true",
            "trust.points.delivered,1001,false",
            "score.odd_hour_end,24,true",
            "score.odd_hour_end,25,false",
            "score.repeat_phone_min,0,false",
            "score.repeat_phone_min,1,true",
            "rescue.max_attempts,10,true",
            "rescue.max_attempts,11,false",
            "rescue.max_attempts,0,false",
            "cost.action.manual,0,true",
            "cost.action.manual,-1,false",
            "cost.action.manual,100000,true",
            "score.margin_thin_below_pct,100,true",
            "score.margin_thin_below_pct,100.5,false",
            "journey.otp_confirm.dropoff,0.05,true",
            "journey.otp_confirm.dropoff,5,false",
            "score.courier_min_success,0.75,true",
            "score.courier_min_success,abc,false",
            "confirm.reminder_minutes,0,false",
            "confirm.reminder_minutes,120,true"
    })
    void allowedRanges(String key, String value, boolean allowed) {
        if (allowed) {
            assertThat(ConfigRules.validate(key, value, DEFAULTS)).isNotNull();
        } else {
            assertThatThrownBy(() -> ConfigRules.validate(key, value, DEFAULTS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void numbersAreTidied() {
        assertThat(ConfigRules.validate("pfail.high", "0.50", DEFAULTS)).isEqualTo("0.5");
        assertThat(ConfigRules.validate("pfail.high", " 0.45 ", DEFAULTS)).isEqualTo("0.45");
        assertThat(ConfigRules.validate("score.penalty.cod", "007", DEFAULTS)).isEqualTo("7");
        assertThat(ConfigRules.validate("score.penalty.cod", "-0", DEFAULTS)).isEqualTo("0");
        assertThat(ConfigRules.validate("loss.packing", "20.00", DEFAULTS)).isEqualTo("20");
        assertThat(ConfigRules.validate("score.high_value_min", "2E3", DEFAULTS)).isEqualTo("2000");
    }

    @Test
    void blankNullAndGarbageAreRefused() {
        for (String bad : new String[]{null, "", "   ", "abc", "12abc", "NaN", "Infinity", "1,5", "--1", "0x10"}) {
            assertThatThrownBy(() -> ConfigRules.validate("score.penalty.cod", bad, DEFAULTS))
                    .as("value " + bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void hugeAndTinyNumbersCannotBlowUpTheServer() {
        assertThatThrownBy(() -> ConfigRules.validate("pfail.high", "1E-999999999", DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigRules.validate("loss.packing", "1E+999999999", DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigRules.validate("loss.packing", "9".repeat(5000), DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConfigRules.validate("pfail.high", "0.0000001", DEFAULTS))   // 7 decimal places
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ConfigRules.validate("pfail.high", "0.000001", DEFAULTS)).isEqualTo("0.000001");
    }

    @Test
    void unknownKeyIsRefused() {
        assertThatThrownBy(() -> ConfigRules.validate("no.such.key", "1", DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown config key");
        assertThat(ConfigRules.describe("no.such.key")).isEqualTo("unknown key");
    }

    @Test
    void errorMessageSaysWhatIsAllowed() {
        assertThatThrownBy(() -> ConfigRules.validate("score.penalty.cod", "150", DEFAULTS))
                .hasMessage("score.penalty.cod must be a whole number from 0 to 100 (got '150')");
        assertThatThrownBy(() -> ConfigRules.validate("pfail.high", "1.5", DEFAULTS))
                .hasMessage("pfail.high must be a number from 0 to 1 (got '1.5')");
        assertThat(ConfigRules.describe("score.penalty.cod")).isEqualTo("a whole number from 0 to 100");
        assertThat(ConfigRules.describe("pfail.high")).isEqualTo("a number from 0 to 1");
    }

    // ------------------------------------------------------------------ ordering rules

    @Test
    void lowerLimitMayNotGoAboveItsUpperLimit() {
        // defaults: score.high_below 40, score.medium_below 70
        assertThatThrownBy(() -> ConfigRules.validate("score.high_below", "80", DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("score.high_below (80) must not be above score.medium_below (70). Change score.medium_below first.");
        assertThat(ConfigRules.validate("score.high_below", "70", DEFAULTS)).isEqualTo("70");   // equal is fine
    }

    @Test
    void upperLimitMayNotGoBelowItsLowerLimit() {
        assertThatThrownBy(() -> ConfigRules.validate("score.medium_below", "30", DEFAULTS))
                .hasMessage("score.medium_below (30) must not be below score.high_below (40). Change score.high_below first.");
    }

    @Test
    void everyOrderedPairIsProtected() {
        String[][] pairs = {
                {"tier.manual_review_below", "-30", "tier.prepaid_only_below"},     // -30 is above -40
                {"score.address_poor_below", "80", "score.address_fair_below"},
                {"score.pincode_medium_rate", "0.5", "score.pincode_high_rate"},
                {"score.odd_hour_start", "10", "score.odd_hour_end"}
        };
        for (String[] pair : pairs) {
            assertThatThrownBy(() -> ConfigRules.validate(pair[0], pair[1], DEFAULTS))
                    .as(pair[0])
                    .hasMessageContaining(pair[2]);
        }
    }

    @Test
    void orderingUsesTheCurrentValueOfTheOtherKey() {
        Map<String, String> current = new HashMap<>(ConfigKeys.DEFAULTS);
        current.put("score.medium_below", "90");

        assertThat(ConfigRules.validate("score.high_below", "80", current::get)).isEqualTo("80");
    }
}
