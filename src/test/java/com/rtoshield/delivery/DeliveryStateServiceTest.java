package com.rtoshield.delivery;

import com.rtoshield.audit.AuditService;
import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.AuditLog;
import com.rtoshield.domain.Courier;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.DeliveryEvent;
import com.rtoshield.domain.DeliveryEventType;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.OrderStatus;
import com.rtoshield.domain.PaymentType;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.recovery.RescueService;
import com.rtoshield.repo.DeliveryEventRepository;
import com.rtoshield.scoring.PincodeStatsService;
import com.rtoshield.testsupport.Fakes;
import com.rtoshield.trust.TrustPointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M6 tests. No database, no Spring, no Mockito: repositories are in-memory fakes and the three helper services
 * (trust points, pincode statistics, rescue) are small subclasses that only write down what they were asked to do.
 *
 * If you add a constructor field to RescueService in M7, change the one line in setUp() that creates it.
 */
class DeliveryStateServiceTest {

    private static final LocalDateTime LONG_AGO = LocalDateTime.now().minusHours(5);

    private final Map<Long, Order> orders = new HashMap<>();
    private final List<DeliveryEvent> savedEvents = new ArrayList<>();
    private final List<String> trustCalls = new ArrayList<>();
    private final List<String> pincodeCalls = new ArrayList<>();
    private final List<String> rescueCalls = new ArrayList<>();
    private final List<AuditLog> auditRows = new ArrayList<>();

    private DeliveryStateService service;

    @BeforeEach
    void setUp() {
        OrderLocker locker = new OrderLocker(null) {
            @Override
            public Optional<Order> lock(Long orderId) {
                return Optional.ofNullable(orders.get(orderId));
            }
        };

        DeliveryEventRepository eventRepo = Fakes.proxy(DeliveryEventRepository.class, (method, args) -> switch (method) {
            case "existsByOrderIdAndAttemptNoAndStatus" -> savedEvents.stream().anyMatch(e ->
                    e.getOrder().getId().equals(args[0])
                            && e.getAttemptNo() == (Integer) args[1]
                            && e.getStatus() == args[2]);
            case "save" -> {
                DeliveryEvent event = (DeliveryEvent) args[0];
                savedEvents.add(event);
                yield event;
            }
            default -> throw new UnsupportedOperationException("DeliveryEventRepository." + method);
        });

        TrustPointsService trust = new TrustPointsService(null, null, null, null) {
            @Override
            public void recordEvent(Long customerId, Long orderId, TrustEventType eventType) {
                trustCalls.add(customerId + ":" + orderId + ":" + eventType);
            }
        };

        PincodeStatsService pincode = new PincodeStatsService(null, null) {
            @Override
            public void recordResult(Long courierId, String pincode, boolean success) {
                pincodeCalls.add(courierId + ":" + pincode + ":" + success);
            }
        };

        // rescue records the order's state AT THE MOMENT it is called: id:failure:status:attemptCount
        RescueService rescue = new RescueService(null, null, null, null, null) {
            @Override
            public void onFailedAttempt(Order order, DeliveryEventType failure) {
                rescueCalls.add(order.getId() + ":" + failure + ":" + order.getStatus() + ":" + order.getAttemptCount());
            }
        };

        AuditService audit = new AuditService(Fakes.auditRepo(auditRows));

        service = new DeliveryStateService(locker, eventRepo, trust, pincode, rescue, audit);
    }

    // ------------------------------------------------------------------ helpers

    private Order newOrder(long id, OrderStatus status, PaymentType payment) {
        Customer customer = new Customer();
        customer.setId(1L);
        Courier courier = new Courier();
        courier.setId(7L);
        courier.setName("QuickPost");

        Order order = new Order();
        order.setId(id);
        order.setCustomer(customer);
        order.setCourier(courier);
        order.setPincode("400001");
        order.setPaymentType(payment);
        order.setStatus(status);
        order.setLastEventAt(LONG_AGO);
        orders.put(id, order);
        return order;
    }

    private Order confirmedCod(long id) {
        return newOrder(id, OrderStatus.CONFIRMED, PaymentType.COD);
    }

    private Order send(long orderId, DeliveryEventType type) {
        return service.handle(new CourierEventRequest(orderId, type, null));
    }

    private Order send(long orderId, DeliveryEventType type, int attemptNo) {
        return service.handle(new CourierEventRequest(orderId, type, attemptNo));
    }

    /** "1:OUT_FOR_DELIVERY" style list of the saved events, in the order they were saved. */
    private List<String> eventList() {
        return savedEvents.stream().map(e -> e.getAttemptNo() + ":" + e.getStatus()).toList();
    }

    // ------------------------------------------------------------------ out for delivery

    @Test
    void outForDeliveryFromConfirmedStartsAttemptOne() {
        confirmedCod(1);

        Order result = send(1, DeliveryEventType.OUT_FOR_DELIVERY);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(result.getAttemptCount()).isEqualTo(0);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY");
        assertThat(result.getLastEventAt()).isAfter(LONG_AGO);
        assertThat(trustCalls).isEmpty();
        assertThat(pincodeCalls).isEmpty();
        assertThat(rescueCalls).isEmpty();

        assertThat(auditRows).hasSize(1);
        assertThat(auditRows.get(0).getOrderId()).isEqualTo(1L);
        assertThat(auditRows.get(0).getDecision()).isEqualTo("DELIVERY_OUT_FOR_DELIVERY");
        assertThat(auditRows.get(0).getReason())
                .isEqualTo("OUT_FOR_DELIVERY on attempt 1: CONFIRMED -> OUT_FOR_DELIVERY");
        assertThat(auditRows.get(0).getInputs())
                .isEqualTo("attempt=1, courier=QuickPost, pincode=400001, payment=COD, failedAttempts=0");
    }

    @Test
    void outForDeliveryFromShippedIsAllowed() {
        newOrder(1, OrderStatus.SHIPPED, PaymentType.COD);

        assertThat(send(1, DeliveryEventType.OUT_FOR_DELIVERY).getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void outForDeliveryTwiceIsCountedOnce() {
        confirmedCod(1);

        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);

        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY");
        assertThat(auditRows).hasSize(1);
    }

    @Test
    void outForDeliveryIsRejectedFromStatusesThatCannotShip() {
        OrderStatus[] blocked = {OrderStatus.CREATED, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.ON_HOLD,
                OrderStatus.HUMAN_REVIEW, OrderStatus.CANCELLED, OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.DELIVERED, OrderStatus.RTO};
        long id = 100;
        for (OrderStatus status : blocked) {
            id++;
            newOrder(id, status, PaymentType.COD);
            long orderId = id;
            assertThatThrownBy(() -> send(orderId, DeliveryEventType.OUT_FOR_DELIVERY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Event OUT_FOR_DELIVERY cannot be applied to order " + orderId + " in status " + status
                            + " (allowed from: [CONFIRMED, SHIPPED, DELIVERY_FAILED, RESCHEDULED])");
            assertThat(orders.get(orderId).getStatus()).isEqualTo(status);
        }
        assertThat(savedEvents).isEmpty();
        assertThat(auditRows).isEmpty();
    }

    // ------------------------------------------------------------------ delivered

    @Test
    void deliveredCodOrderGivesTrustAndPincodeSuccess() {
        confirmedCod(1);

        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        Order result = send(1, DeliveryEventType.DELIVERED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(result.getAttemptCount()).isEqualTo(0);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:DELIVERED");
        assertThat(trustCalls).containsExactly("1:1:DELIVERED");
        assertThat(pincodeCalls).containsExactly("7:400001:true");
        assertThat(rescueCalls).isEmpty();
        assertThat(auditRows).hasSize(2);
        assertThat(auditRows.get(1).getDecision()).isEqualTo("DELIVERY_DELIVERED");
        assertThat(auditRows.get(1).getReason()).isEqualTo("DELIVERED on attempt 1: OUT_FOR_DELIVERY -> DELIVERED");
    }

    @Test
    void deliveredPrepaidOrderAlsoGivesPaidOnlinePoints() {
        newOrder(1, OrderStatus.CONFIRMED, PaymentType.PREPAID);

        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.DELIVERED);

        assertThat(trustCalls).containsExactly("1:1:DELIVERED", "1:1:PAID_ONLINE");
    }

    @Test
    void deliveredTwiceHasNoSecondEffect() {
        confirmedCod(1);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.DELIVERED);
        int auditBefore = auditRows.size();

        Order again = send(1, DeliveryEventType.DELIVERED);

        assertThat(again.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:DELIVERED");
        assertThat(trustCalls).hasSize(1);
        assertThat(pincodeCalls).hasSize(1);
        assertThat(auditRows).hasSize(auditBefore);
    }

    @Test
    void deliveredIsRejectedBeforeTheParcelIsOutForDelivery() {
        confirmedCod(1);

        assertThatThrownBy(() -> send(1, DeliveryEventType.DELIVERED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event DELIVERED cannot be applied to order 1 in status CONFIRMED (allowed from: [OUT_FOR_DELIVERY])");

        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orders.get(1L).getLastEventAt()).isEqualTo(LONG_AGO);
        assertThat(savedEvents).isEmpty();
        assertThat(trustCalls).isEmpty();
        assertThat(pincodeCalls).isEmpty();
        assertThat(auditRows).isEmpty();
    }

    @Test
    void deliveredWithoutCourierSkipsPincodeStatistics() {
        Order order = confirmedCod(1);
        order.setCourier(null);

        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.DELIVERED);

        assertThat(trustCalls).containsExactly("1:1:DELIVERED");
        assertThat(pincodeCalls).isEmpty();
        assertThat(auditRows.get(0).getInputs()).contains("courier=none");
    }

    // ------------------------------------------------------------------ failures

    @Test
    void refusedMovesOrderToDeliveryFailedAndStartsRescue() {
        confirmedCod(1);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);

        Order result = send(1, DeliveryEventType.REFUSED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:REFUSED");
        assertThat(trustCalls).containsExactly("1:1:REFUSED");
        assertThat(pincodeCalls).containsExactly("7:400001:false");
        // rescue sees the order AFTER the status and the attempt count were updated
        assertThat(rescueCalls).containsExactly("1:REFUSED:DELIVERY_FAILED:1");
        assertThat(auditRows.get(1).getReason()).isEqualTo("REFUSED on attempt 1: OUT_FOR_DELIVERY -> DELIVERY_FAILED");
        assertThat(auditRows.get(1).getInputs())
                .isEqualTo("attempt=1, courier=QuickPost, pincode=400001, payment=COD, failedAttempts=1");
    }

    @Test
    void everyFailureTypeGivesItsOwnTrustEvent() {
        DeliveryEventType[] failures = {DeliveryEventType.CUSTOMER_UNREACHABLE, DeliveryEventType.REFUSED,
                DeliveryEventType.WRONG_ADDRESS};
        String[] expectedTrust = {"UNREACHABLE", "REFUSED", "FAKE_ADDRESS"};

        for (int i = 0; i < failures.length; i++) {
            long id = i + 1;
            confirmedCod(id);
            send(id, DeliveryEventType.OUT_FOR_DELIVERY);
            send(id, failures[i]);
            assertThat(trustCalls.get(i)).isEqualTo("1:" + id + ":" + expectedTrust[i]);
            assertThat(orders.get(id).getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
            assertThat(orders.get(id).getAttemptCount()).isEqualTo(1);
        }
        assertThat(trustCalls).hasSize(3);
        assertThat(pincodeCalls).hasSize(3);
        assertThat(rescueCalls).hasSize(3);
    }

    @Test
    void sameFailureTwiceIsCountedOnce() {
        confirmedCod(1);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.REFUSED);
        int auditBefore = auditRows.size();

        Order again = send(1, DeliveryEventType.REFUSED);

        assertThat(again.getAttemptCount()).isEqualTo(1);
        assertThat(again.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:REFUSED");
        assertThat(trustCalls).hasSize(1);
        assertThat(pincodeCalls).hasSize(1);
        assertThat(rescueCalls).hasSize(1);
        assertThat(auditRows).hasSize(auditBefore);
    }

    @Test
    void failureRepeatedAfterRescueRescheduledTheOrderIsStillIgnored() {
        confirmedCod(1);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.REFUSED);
        orders.get(1L).setStatus(OrderStatus.RESCHEDULED); // what the rescue step will do

        Order again = send(1, DeliveryEventType.REFUSED);

        assertThat(again.getStatus()).isEqualTo(OrderStatus.RESCHEDULED);
        assertThat(again.getAttemptCount()).isEqualTo(1);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:REFUSED");
        assertThat(trustCalls).hasSize(1);
        assertThat(rescueCalls).hasSize(1);
    }

    @Test
    void failureWithoutOutForDeliveryIsRejected() {
        confirmedCod(1);

        assertThatThrownBy(() -> send(1, DeliveryEventType.REFUSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event REFUSED cannot be applied to order 1 in status CONFIRMED (allowed from: [OUT_FOR_DELIVERY])");

        assertThat(savedEvents).isEmpty();
        assertThat(trustCalls).isEmpty();
        assertThat(rescueCalls).isEmpty();
        assertThat(orders.get(1L).getAttemptCount()).isEqualTo(0);
    }

    // ------------------------------------------------------------------ several attempts

    @Test
    void secondAttemptCanSucceedAfterAFailure() {
        confirmedCod(1);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.CUSTOMER_UNREACHABLE);
        // the stub rescue leaves the status at DELIVERY_FAILED: a new attempt must still be possible

        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        Order result = send(1, DeliveryEventType.DELIVERED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:CUSTOMER_UNREACHABLE",
                "2:OUT_FOR_DELIVERY", "2:DELIVERED");
        assertThat(trustCalls).containsExactly("1:1:UNREACHABLE", "1:1:DELIVERED");
        assertThat(pincodeCalls).containsExactly("7:400001:false", "7:400001:true");
    }

    @Test
    void threeFailedAttemptsAreNumberedOneTwoThree() {
        confirmedCod(1);

        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.CUSTOMER_UNREACHABLE);
        orders.get(1L).setStatus(OrderStatus.RESCHEDULED); // rescue after attempt 1
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        send(1, DeliveryEventType.CUSTOMER_UNREACHABLE);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY);
        Order result = send(1, DeliveryEventType.REFUSED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(result.getAttemptCount()).isEqualTo(3);
        assertThat(eventList()).containsExactly(
                "1:OUT_FOR_DELIVERY", "1:CUSTOMER_UNREACHABLE",
                "2:OUT_FOR_DELIVERY", "2:CUSTOMER_UNREACHABLE",
                "3:OUT_FOR_DELIVERY", "3:REFUSED");
        // M6 reports every failure; the "count once per order and event type" rule is applied by TrustPointsService (M8)
        assertThat(trustCalls).containsExactly("1:1:UNREACHABLE", "1:1:UNREACHABLE", "1:1:REFUSED");
        assertThat(pincodeCalls).containsExactly("7:400001:false", "7:400001:false", "7:400001:false");
        assertThat(rescueCalls).containsExactly(
                "1:CUSTOMER_UNREACHABLE:DELIVERY_FAILED:1",
                "1:CUSTOMER_UNREACHABLE:DELIVERY_FAILED:2",
                "1:REFUSED:DELIVERY_FAILED:3");
    }

    // ------------------------------------------------------------------ delays

    @Test
    void delayedOnlyWritesDownTheEvent() {
        newOrder(1, OrderStatus.SHIPPED, PaymentType.COD);

        Order result = send(1, DeliveryEventType.DELAYED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(result.getAttemptCount()).isEqualTo(0);
        assertThat(result.getLastEventAt()).isAfter(LONG_AGO);
        assertThat(eventList()).containsExactly("1:DELAYED");
        assertThat(trustCalls).isEmpty();
        assertThat(pincodeCalls).isEmpty();
        assertThat(rescueCalls).isEmpty();
        assertThat(auditRows.get(0).getDecision()).isEqualTo("DELIVERY_DELAYED");
        assertThat(auditRows.get(0).getReason()).isEqualTo("DELAYED on attempt 1: status stays SHIPPED");
    }

    @Test
    void delayedTwiceOnTheSameAttemptIsCountedOnce() {
        newOrder(1, OrderStatus.OUT_FOR_DELIVERY, PaymentType.COD);

        send(1, DeliveryEventType.DELAYED);
        send(1, DeliveryEventType.DELAYED);

        assertThat(eventList()).containsExactly("1:DELAYED");
        assertThat(auditRows).hasSize(1);
    }

    @Test
    void delayedIsRejectedOnceTheOrderIsDelivered() {
        newOrder(1, OrderStatus.DELIVERED, PaymentType.COD);

        assertThatThrownBy(() -> send(1, DeliveryEventType.DELAYED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be applied to order 1 in status DELIVERED");
        assertThat(savedEvents).isEmpty();
    }

    // ------------------------------------------------------------------ attemptNo sent by the courier

    @Test
    void attemptNoMatchingTheCurrentAttemptIsAccepted() {
        confirmedCod(1);

        Order result = send(1, DeliveryEventType.OUT_FOR_DELIVERY, 1);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY");
    }

    @Test
    void attemptNoThatSkipsAheadIsRejected() {
        confirmedCod(1);

        assertThatThrownBy(() -> send(1, DeliveryEventType.OUT_FOR_DELIVERY, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attemptNo 2 does not match the current attempt 1 of order 1");

        assertThat(savedEvents).isEmpty();
        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void attemptNoBelowOneIsABadRequest() {
        confirmedCod(1);

        assertThatThrownBy(() -> send(1, DeliveryEventType.OUT_FOR_DELIVERY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attemptNo must be 1 or more");
    }

    @Test
    void retriedEventsWithAttemptNoAreIgnoredEvenAfterTheOrderMovedOn() {
        confirmedCod(1);
        send(1, DeliveryEventType.OUT_FOR_DELIVERY, 1);
        send(1, DeliveryEventType.REFUSED, 1);
        orders.get(1L).setStatus(OrderStatus.RESCHEDULED);
        int auditBefore = auditRows.size();

        // the courier retries both webhooks of attempt 1
        send(1, DeliveryEventType.REFUSED, 1);
        Order result = send(1, DeliveryEventType.OUT_FOR_DELIVERY, 1);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.RESCHEDULED); // attempt 2 did NOT start
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(eventList()).containsExactly("1:OUT_FOR_DELIVERY", "1:REFUSED");
        assertThat(trustCalls).hasSize(1);
        assertThat(auditRows).hasSize(auditBefore);
    }

    // ------------------------------------------------------------------ missing order

    @Test
    void unknownOrderIsNotFound() {
        assertThatThrownBy(() -> send(99, DeliveryEventType.DELIVERED))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order 99 not found");
        assertThat(savedEvents).isEmpty();
    }
}
