package com.rtoshield.config;

import com.rtoshield.audit.AuditService;
import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.AuditLog;
import com.rtoshield.domain.ConfigEntry;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reading and changing settings at run time, and the audit rows each change leaves behind. */
class ConfigAdminServiceTest {

    private final Map<String, ConfigEntry> rows = new HashMap<>();
    private final List<AuditLog> auditRows = new ArrayList<>();
    private ConfigAdminService admin;
    private ConfigService config;

    @BeforeEach
    void setUp() {
        rows.clear();
        auditRows.clear();
        config = new ConfigService(Fakes.configRepo(rows));
        admin = new ConfigAdminService(config, new AuditService(Fakes.auditRepo(auditRows)));
    }

    @Test
    void listShowsEveryKeyWithItsDefault() {
        List<ConfigView> views = admin.list();

        assertThat(views).hasSize(ConfigKeys.DEFAULTS.size());
        assertThat(views.get(0).key()).isEqualTo("trust.points.delivered");
        for (ConfigView view : views) {
            assertThat(view.value()).isEqualTo(view.defaultValue());
            assertThat(view.changed()).isFalse();
            assertThat(view.allowed()).isNotBlank();
        }
    }

    @Test
    void updateChangesTheValueAndWritesAnAuditRow() {
        ConfigView view = admin.update("score.penalty.cod", "30");

        assertThat(view.value()).isEqualTo("30");
        assertThat(view.defaultValue()).isEqualTo("15");
        assertThat(view.changed()).isTrue();
        assertThat(view.allowed()).isEqualTo("a whole number from 0 to 100");
        assertThat(config.getInt("score.penalty.cod")).isEqualTo(30);     // the next order sees it immediately

        assertThat(auditRows).hasSize(1);
        AuditLog row = auditRows.get(0);
        assertThat(row.getOrderId()).isNull();
        assertThat(row.getDecision()).isEqualTo("CONFIG_CHANGE");
        assertThat(row.getReason()).isEqualTo("score.penalty.cod: 15 -> 30");
        assertThat(row.getInputs()).isEqualTo("key=score.penalty.cod, old=15, new=30");
    }

    @Test
    void getShowsOneSetting() {
        admin.update("pfail.high", "0.5");

        ConfigView view = admin.get("pfail.high");

        assertThat(view.value()).isEqualTo("0.5");
        assertThat(view.defaultValue()).isEqualTo("0.45");
        assertThat(view.changed()).isTrue();
    }

    @Test
    void updateTidiesNumbers() {
        assertThat(admin.update("pfail.high", "0.50").value()).isEqualTo("0.5");
        assertThat(admin.update("score.penalty.cod", "007").value()).isEqualTo("7");
    }

    @Test
    void updatingToTheSameValueWritesNothing() {
        ConfigView view = admin.update("score.penalty.cod", "15");

        assertThat(view.changed()).isFalse();
        assertThat(auditRows).isEmpty();
        assertThat(rows).isEmpty();
    }

    @Test
    void updatingBackToTheDefaultShowsChangedFalse() {
        admin.update("score.penalty.cod", "30");

        ConfigView view = admin.update("score.penalty.cod", "15");

        assertThat(view.changed()).isFalse();
        assertThat(auditRows).hasSize(2);
    }

    @Test
    void invalidValuesAreRejectedAndNothingChanges() {
        for (String bad : new String[]{"abc", "150", "1.5", "", "   ", "-1"}) {
            assertThatThrownBy(() -> admin.update("score.penalty.cod", bad))
                    .as("value '" + bad + "'")
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> admin.update("score.penalty.cod", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(config.getInt("score.penalty.cod")).isEqualTo(15);
        assertThat(rows).isEmpty();
        assertThat(auditRows).isEmpty();
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThatThrownBy(() -> admin.update("no.such.key", "1")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> admin.get("no.such.key")).isInstanceOf(NotFoundException.class);
        assertThat(auditRows).isEmpty();
    }

    @Test
    void orderingRulesProtectConsistencyAndCanBeFollowedInTheRightOrder() {
        assertThatThrownBy(() -> admin.update("score.high_below", "80"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score.medium_below");
        assertThat(auditRows).isEmpty();

        admin.update("score.medium_below", "90");
        admin.update("score.high_below", "80");

        assertThat(config.getInt("score.high_below")).isEqualTo(80);
        assertThat(config.getInt("score.medium_below")).isEqualTo(90);
    }

    @Test
    void resetAllRestoresDefaultsAndWritesOneAuditRow() {
        admin.update("score.penalty.cod", "30");
        admin.update("pfail.high", "0.6");
        auditRows.clear();

        int changed = admin.resetAll();

        assertThat(changed).isEqualTo(2);
        assertThat(config.getInt("score.penalty.cod")).isEqualTo(15);
        assertThat(config.getDecimal("pfail.high")).isEqualByComparingTo("0.45");
        assertThat(auditRows).hasSize(1);
        assertThat(auditRows.get(0).getDecision()).isEqualTo("CONFIG_RESET");
        assertThat(auditRows.get(0).getReason())
                .isEqualTo("2 setting(s) reset to defaults: pfail.high, score.penalty.cod");
    }

    @Test
    void resetAllWithNothingChangedDoesNothing() {
        assertThat(admin.resetAll()).isEqualTo(0);
        assertThat(auditRows).isEmpty();
    }

    @Test
    void resetAllWorksEvenWhenTheCurrentValuesAreInAnUnusualOrder() {
        admin.update("score.medium_below", "90");
        admin.update("score.high_below", "85");
        auditRows.clear();

        assertThat(admin.resetAll()).isEqualTo(2);
        assertThat(config.getInt("score.high_below")).isEqualTo(40);
        assertThat(config.getInt("score.medium_below")).isEqualTo(70);
    }
}
