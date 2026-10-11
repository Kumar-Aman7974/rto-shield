package com.rtoshield.scoring;

import com.rtoshield.domain.CourierPincodeStat;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PincodeStatsServiceTest {

    private final Fakes.StatStore store = new Fakes.StatStore();
    private PincodeStatsService service;

    @BeforeEach
    void setUp() {
        store.rows.clear();
        service = new PincodeStatsService(store.repo(), Fakes.courierRepo());
    }

    @Test
    void unknownPincodeHasNoHistoryAndSafeDefaults() {
        assertThat(service.hasHistory("999999")).isFalse();
        assertThat(service.refusalRate("999999")).isEqualTo(0.20);
        assertThat(service.bestCourierSuccessRate("999999")).isEqualTo(0.80);
    }

    @Test
    void ratesMatchTheSeedNumbersForPincode110059() {
        store.setPincode("110059", 55, 70, 50);

        assertThat(service.hasHistory("110059")).isTrue();
        assertThat(service.refusalRate("110059")).isCloseTo(125.0 / 300.0, within(1e-9));
        assertThat(service.bestCourierSuccessRate("110059")).isEqualTo(0.70);
        assertThat(service.courierSuccessRate(1L, "110059")).isEqualTo(0.55);
    }

    @Test
    void successfulDeliveryAddsOneAttemptAndOneSuccess() {
        store.setPincode("110059", 55, 70, 50);

        service.recordResult(1L, "110059", true);

        CourierPincodeStat row = store.rows.get(0);
        assertThat(row.getAttempts()).isEqualTo(101);
        assertThat(row.getSuccesses()).isEqualTo(56);
    }

    @Test
    void failedDeliveryAddsOnlyAnAttempt() {
        store.setPincode("110059", 55, 70, 50);

        service.recordResult(1L, "110059", false);

        CourierPincodeStat row = store.rows.get(0);
        assertThat(row.getAttempts()).isEqualTo(101);
        assertThat(row.getSuccesses()).isEqualTo(55);
    }

    @Test
    void firstResultForANewPincodeCreatesTheRow() {
        service.recordResult(2L, "700001", true);

        assertThat(store.rows).hasSize(1);
        CourierPincodeStat row = store.rows.get(0);
        assertThat(row.getPincode()).isEqualTo("700001");
        assertThat(row.getCourier().getId()).isEqualTo(2L);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getSuccesses()).isEqualTo(1);
    }

    @Test
    void nullPincodeFallsBackToTheDefaultRate() {
        assertThat(service.courierSuccessRate(1L, null)).isEqualTo(0.80);
    }

    @Test
    void rowWithNoAttemptsFallsBackToTheDefaultRate() {
        store.setPincode("700001", 0, 0, 0);
        store.rows.forEach(row -> {
            row.setAttempts(0);
            row.setSuccesses(0);
        });

        assertThat(service.courierSuccessRate(1L, "700001")).isEqualTo(0.80);
        assertThat(service.hasHistory("700001")).isFalse();
    }
}
