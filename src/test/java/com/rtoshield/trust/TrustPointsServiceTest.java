package com.rtoshield.trust;

import com.rtoshield.audit.AuditService;
import com.rtoshield.common.NotFoundException;
import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.AuditLog;
import com.rtoshield.domain.ConfigEntry;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.CustomerTier;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.domain.TrustLedgerEntry;
import com.rtoshield.repo.ConfigEntryRepository;
import com.rtoshield.repo.CustomerRepository;
import com.rtoshield.repo.TrustLedgerRepository;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests: no database, no Spring, no Mockito.
 * The three repositories are replaced by tiny in-memory fakes, so these tests run in under a second
 * on any Java version. Run with: mvn clean test
 */
class TrustPointsServiceTest {

    private interface Handler {
        Object handle(String method, Object[] args);
    }

    private final Map<String, ConfigEntry> configRows = new HashMap<>();
    private final List<TrustLedgerEntry> ledgerRows = new ArrayList<>();
    private final List<AuditLog> auditRows = new ArrayList<>();
    private Customer customer;
    private TrustPointsService service;

    @BeforeEach
    void setUp() {
        configRows.clear();
        ledgerRows.clear();
        auditRows.clear();
        customer = new Customer();
        customer.setId(1L);
        customer.setName("Test Customer");
        customer.setPhone("9999999999");
        customer.setTrustBalance(0);
        customer.setTier(CustomerTier.GOOD);
        service = new TrustPointsService(
                new ConfigService(fakeConfigRepo()), fakeCustomerRepo(), fakeLedgerRepo(),
                new AuditService(Fakes.auditRepo(auditRows)));
    }

    // ------------------------------------------------------------------ points

    @Test
    void refusedDeducts30AndWritesOneLedgerRow() {
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);

        assertThat(customer.getTrustBalance()).isEqualTo(-30);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.CAUTION);
        assertThat(ledgerRows).hasSize(1);
        TrustLedgerEntry row = ledgerRows.get(0);
        assertThat(row.getDelta()).isEqualTo(-30);
        assertThat(row.getOrderId()).isEqualTo(101L);
        assertThat(row.getEventType()).isEqualTo(TrustEventType.REFUSED);
        assertThat(row.getCustomer()).isSameAs(customer);
    }

    @Test
    void deliveredAdds10() {
        service.recordEvent(1L, 101L, TrustEventType.DELIVERED);

        assertThat(customer.getTrustBalance()).isEqualTo(10);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.GOOD);
    }

    @Test
    void everyEventTypeUsesItsConfiguredPoints() {
        int expected = 0;
        long orderId = 500;
        for (TrustEventType type : TrustEventType.values()) {
            expected += Integer.parseInt(ConfigKeys.DEFAULTS.get(type.configKey()));
            service.recordEvent(1L, orderId++, type);
        }
        // 10 + 5 + 5 - 30 - 15 - 20 - 25
        assertThat(customer.getTrustBalance()).isEqualTo(expected).isEqualTo(-70);
        assertThat(ledgerRows).hasSize(TrustEventType.values().length);
    }

    // ------------------------------------------------------------ count once

    @Test
    void sameEventOnSameOrderCountsOnlyOnce() {
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);

        assertThat(customer.getTrustBalance()).isEqualTo(-30);
        assertThat(ledgerRows).hasSize(1);
    }

    @Test
    void differentEventsOnSameOrderBothCount() {
        service.recordEvent(1L, 101L, TrustEventType.UNREACHABLE);
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);

        assertThat(customer.getTrustBalance()).isEqualTo(-45);
        assertThat(ledgerRows).hasSize(2);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.PREPAID_ONLY);
    }

    @Test
    void eventWithoutOrderIdIsAlwaysRecorded() {
        service.recordEvent(1L, null, TrustEventType.REFUSED);
        service.recordEvent(1L, null, TrustEventType.REFUSED);

        assertThat(customer.getTrustBalance()).isEqualTo(-60);
        assertThat(ledgerRows).hasSize(2);
    }

    // ------------------------------------------------------------------ tiers

    @Test
    void crossingMinus40BecomesPrepaidOnly() {
        customer.setTrustBalance(-20);

        service.recordEvent(1L, 101L, TrustEventType.REFUSED);   // -50

        assertThat(customer.getTrustBalance()).isEqualTo(-50);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.PREPAID_ONLY);
    }

    @Test
    void crossingMinus100BecomesManualReview() {
        customer.setTrustBalance(-80);

        service.recordEvent(1L, 101L, TrustEventType.REFUSED);   // -110

        assertThat(customer.getTrustBalance()).isEqualTo(-110);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.MANUAL_REVIEW);
    }

    @Test
    void exactlyMinus40IsStillCaution() {
        customer.setTrustBalance(-10);

        service.recordEvent(1L, 101L, TrustEventType.REFUSED);   // -40

        assertThat(customer.getTrustBalance()).isEqualTo(-40);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.CAUTION);
    }

    @Test
    void exactlyMinus100IsStillPrepaidOnly() {
        customer.setTrustBalance(-70);

        service.recordEvent(1L, 101L, TrustEventType.REFUSED);   // -100

        assertThat(customer.getTrustBalance()).isEqualTo(-100);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.PREPAID_ONLY);
    }

    @ParameterizedTest
    @CsvSource({
            "0,GOOD", "5,GOOD", "-1,CAUTION", "-40,CAUTION",
            "-41,PREPAID_ONLY", "-100,PREPAID_ONLY", "-101,MANUAL_REVIEW"
    })
    void tierBoundaries(int balance, CustomerTier expected) {
        assertThat(service.tierFor(balance)).isEqualTo(expected);
    }

    @Test
    void goodDeliveriesClimbBackToGood() {
        customer.setTrustBalance(-45);
        customer.setTier(CustomerTier.PREPAID_ONLY);

        service.recordEvent(1L, 1L, TrustEventType.DELIVERED);   // -35
        assertThat(customer.getTier()).isEqualTo(CustomerTier.CAUTION);

        for (long order = 2; order <= 5; order++) {
            service.recordEvent(1L, order, TrustEventType.DELIVERED);
        }
        // -35 -> -25 -> -15 -> -5 -> 5
        assertThat(customer.getTrustBalance()).isEqualTo(5);
        assertThat(customer.getTier()).isEqualTo(CustomerTier.GOOD);
    }

    // ----------------------------------------------------------- config + errors

    @Test
    void changingConfigChangesThePointsImmediately() {
        configRows.put("trust.points.refused", new ConfigEntry("trust.points.refused", "-50", "test"));

        service.recordEvent(1L, 101L, TrustEventType.REFUSED);

        assertThat(customer.getTrustBalance()).isEqualTo(-50);
        assertThat(ledgerRows.get(0).getDelta()).isEqualTo(-50);
    }

    @Test
    void unknownCustomerThrowsNotFound() {
        assertThatThrownBy(() -> service.recordEvent(99L, 101L, TrustEventType.REFUSED))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
        assertThat(ledgerRows).isEmpty();
    }

    // ------------------------------------------------------------------ audit log

    @Test
    void everyRecordedEventWritesOneAuditRow() {
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);

        assertThat(auditRows).hasSize(1);
        AuditLog row = auditRows.get(0);
        assertThat(row.getOrderId()).isEqualTo(101L);
        assertThat(row.getDecision()).isEqualTo("TRUST_REFUSED");
        assertThat(row.getReason()).isEqualTo("REFUSED on order 101: -30 points, balance 0 -> -30, tier GOOD -> CAUTION");
        assertThat(row.getInputs()).isEqualTo("customer=1, delta=-30, balance=-30, tier=CAUTION");
    }

    @Test
    void positiveEventsShowAPlusSign() {
        customer.setTrustBalance(30);

        service.recordEvent(1L, 7L, TrustEventType.DELIVERED);

        assertThat(auditRows.get(0).getReason())
                .isEqualTo("DELIVERED on order 7: +10 points, balance 30 -> 40, tier GOOD -> GOOD");
    }

    @Test
    void eventWithoutAnOrderSaysSo() {
        service.recordEvent(1L, null, TrustEventType.REFUSED);

        assertThat(auditRows.get(0).getReason()).startsWith("REFUSED with no order: -30 points");
    }

    @Test
    void ignoredDuplicateAndUnknownCustomerWriteNoAuditRow() {
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);
        service.recordEvent(1L, 101L, TrustEventType.REFUSED);
        assertThat(auditRows).hasSize(1);

        assertThatThrownBy(() -> service.recordEvent(99L, 5L, TrustEventType.REFUSED))
                .isInstanceOf(NotFoundException.class);
        assertThat(auditRows).hasSize(1);
    }

    // ------------------------------------------------------------- in-memory fakes

    private ConfigEntryRepository fakeConfigRepo() {
        return proxy(ConfigEntryRepository.class, (method, args) -> switch (method) {
            case "findById" -> Optional.ofNullable(configRows.get((String) args[0]));
            case "existsById" -> configRows.containsKey((String) args[0]);
            default -> throw new UnsupportedOperationException("ConfigEntryRepository." + method);
        });
    }

    private CustomerRepository fakeCustomerRepo() {
        return proxy(CustomerRepository.class, (method, args) -> switch (method) {
            case "findByIdForUpdate" -> Optional.ofNullable(
                    Objects.equals(customer.getId(), args[0]) ? customer : null);
            case "save" -> args[0];
            default -> throw new UnsupportedOperationException("CustomerRepository." + method);
        });
    }

    private TrustLedgerRepository fakeLedgerRepo() {
        return proxy(TrustLedgerRepository.class, (method, args) -> switch (method) {
            case "existsByOrderIdAndEventType" -> ledgerRows.stream().anyMatch(
                    e -> Objects.equals(e.getOrderId(), args[0]) && e.getEventType() == args[1]);
            case "save" -> {
                ledgerRows.add((TrustLedgerEntry) args[0]);
                yield args[0];
            }
            default -> throw new UnsupportedOperationException("TrustLedgerRepository." + method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> handler.handle(method.getName(), args == null ? new Object[0] : args));
    }
}
