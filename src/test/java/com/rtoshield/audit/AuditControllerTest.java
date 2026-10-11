package com.rtoshield.audit;

import com.rtoshield.domain.AuditLog;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditControllerTest {

    private final List<AuditLog> rows = new ArrayList<>();
    private AuditService service;
    private AuditController controller;

    @BeforeEach
    void setUp() {
        rows.clear();
        service = new AuditService(Fakes.auditRepo(rows));
        controller = new AuditController(Fakes.auditRepo(rows));
    }

    @Test
    void withoutAnOrderTheNewestRowsComeFirst() {
        service.record(1L, "A", "first", "i1");
        service.record(2L, "B", "second", "i2");
        service.record(1L, "C", "third", "i3");

        List<AuditController.AuditView> result = controller.list(null, 50);

        assertThat(result).extracting(AuditController.AuditView::decision).containsExactly("C", "B", "A");
        assertThat(result).extracting(AuditController.AuditView::id).containsExactly(3L, 2L, 1L);
    }

    @Test
    void anOrderFilterShowsOnlyThatOrderOldestFirst() {
        service.record(1L, "A", "first", "i1");
        service.record(2L, "B", "second", "i2");
        service.record(1L, "C", "third", "i3");

        List<AuditController.AuditView> result = controller.list(1L, 50);

        assertThat(result).extracting(AuditController.AuditView::decision).containsExactly("A", "C");
    }

    @Test
    void anUnknownOrderGivesAnEmptyList() {
        service.record(1L, "A", "first", "i1");

        assertThat(controller.list(999L, 50)).isEmpty();
    }

    @Test
    void theLimitIsApplied() {
        for (int i = 1; i <= 5; i++) {
            service.record(1L, "D" + i, "reason", "inputs");
        }

        assertThat(controller.list(null, 2)).extracting(AuditController.AuditView::id).containsExactly(5L, 4L);
        assertThat(controller.list(1L, 1)).extracting(AuditController.AuditView::decision).containsExactly("D1");
    }

    @Test
    void theLimitMustBeBetween1And200() {
        assertThatThrownBy(() -> controller.list(null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.list(null, 201)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.list(null, -5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(controller.list(null, 1)).isEmpty();
        assertThat(controller.list(null, 200)).isEmpty();
    }

    @Test
    void everyFieldIsShown() {
        service.record(7L, "JOURNEY_FULL_COD", "because", "score=95");

        AuditController.AuditView view = controller.list(7L, 50).get(0);

        assertThat(view.orderId()).isEqualTo(7L);
        assertThat(view.decision()).isEqualTo("JOURNEY_FULL_COD");
        assertThat(view.reason()).isEqualTo("because");
        assertThat(view.inputs()).isEqualTo("score=95");
    }

    @Test
    void configChangesHaveNoOrderAndAreStillListed() {
        service.record(null, "CONFIG_CHANGE", "x: 1 -> 2", "key=x");

        List<AuditController.AuditView> result = controller.list(null, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isNull();
    }
}
