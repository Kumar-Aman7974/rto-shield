package com.rtoshield.delivery;

import com.rtoshield.audit.AuditService;
import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.Courier;
import com.rtoshield.domain.DeliveryEvent;
import com.rtoshield.domain.DeliveryEventType;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.OrderStatus;
import com.rtoshield.domain.PaymentType;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.recovery.RescueService;
import com.rtoshield.repo.DeliveryEventRepository;
import com.rtoshield.scoring.PincodeStatsService;
import com.rtoshield.trust.TrustPointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * M6: receives courier events and keeps the order's state correct.
 *
 * Order of work inside handle():
 *   1. find and lock the order (404 if missing)
 *   2. same (order + attempt + status) seen before -> ignore it, no side effects at all
 *   3. event not allowed from the order's current status -> 409, nothing is saved
 *   4. save the DeliveryEvent, update status / attemptCount / lastEventAt, write one audit row
 *   5. side effects: trust points, pincode statistics, rescue (M7)
 * Everything runs in ONE transaction: if any step throws, nothing is saved.
 *
 * Attempt numbers: order.attemptCount counts FAILED attempts. The attempt an event belongs to is
 *   - a failure event while the order is OUT_FOR_DELIVERY: attemptCount + 1 (the attempt in progress)
 *   - a failure event in any other status: attemptCount (a retry of the failure that was already counted)
 *   - every other event: attemptCount + 1
 * A courier may send attemptNo to be explicit. It must match that number, or the event is a repeat of an event we already hold.
 */
@Service
public class DeliveryStateService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStateService.class);

    /** A new attempt can start from these statuses (FULL_COD orders are CONFIRMED and never pass through SHIPPED). */
    private static final Set<OrderStatus> CAN_START_ATTEMPT =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERY_FAILED, OrderStatus.RESCHEDULED);

    /** DELIVERED and every failure can only happen while the parcel is out for delivery. */
    private static final Set<OrderStatus> CAN_FINISH_ATTEMPT = EnumSet.of(OrderStatus.OUT_FOR_DELIVERY);

    /** A delay is only a note on the order: it does not change the status. */
    private static final Set<OrderStatus> CAN_BE_DELAYED =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY,
                    OrderStatus.DELIVERY_FAILED, OrderStatus.RESCHEDULED);

    private final OrderLocker locker;
    private final DeliveryEventRepository events;
    private final TrustPointsService trustPoints;
    private final PincodeStatsService pincodeStats;
    private final RescueService rescue;
    private final AuditService audit;

    public DeliveryStateService(OrderLocker locker,
                                DeliveryEventRepository events,
                                TrustPointsService trustPoints,
                                PincodeStatsService pincodeStats,
                                RescueService rescue,
                                AuditService audit) {
        this.locker = locker;
        this.events = events;
        this.trustPoints = trustPoints;
        this.pincodeStats = pincodeStats;
        this.rescue = rescue;
        this.audit = audit;
    }

    @Transactional
    public Order handle(CourierEventRequest request) {
        Long orderId = request.orderId();
        DeliveryEventType type = request.status();
        Integer suppliedAttempt = request.attemptNo();

        if (suppliedAttempt != null && suppliedAttempt < 1) {
            throw new IllegalArgumentException("attemptNo must be 1 or more");
        }

        Order order = locker.lock(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        int expectedAttempt = expectedAttempt(order, type);
        int attempt = suppliedAttempt != null ? suppliedAttempt : expectedAttempt;

        // 2. a repeat changes nothing
        if (attempt >= 1 && events.existsByOrderIdAndAttemptNoAndStatus(orderId, attempt, type)) {
            log.info("Courier event ignored (already counted): order={}, attempt={}, status={}", orderId, attempt, type);
            return order;
        }

        // 3. impossible moves
        OrderStatus from = order.getStatus();
        Set<OrderStatus> allowed = allowedFrom(type);
        if (!allowed.contains(from)) {
            throw new IllegalStateException("Event " + type + " cannot be applied to order " + orderId
                    + " in status " + from + " (allowed from: " + allowed + ")");
        }
        if (suppliedAttempt != null && suppliedAttempt != expectedAttempt) {
            throw new IllegalStateException("attemptNo " + suppliedAttempt + " does not match the current attempt "
                    + expectedAttempt + " of order " + orderId);
        }

        // 4. record the event and move the order
        DeliveryEvent event = new DeliveryEvent();
        event.setOrder(order);
        event.setAttemptNo(attempt);
        event.setStatus(type);
        events.save(event);

        switch (type) {
            case OUT_FOR_DELIVERY -> order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            case DELIVERED -> order.setStatus(OrderStatus.DELIVERED);
            case CUSTOMER_UNREACHABLE, REFUSED, WRONG_ADDRESS -> {
                order.setStatus(OrderStatus.DELIVERY_FAILED);
                order.setAttemptCount(order.getAttemptCount() + 1);
            }
            case DELAYED -> {
                // status stays as it is
            }
        }
        order.setLastEventAt(LocalDateTime.now());

        Courier courier = order.getCourier();
        String reason = from == order.getStatus()
                ? type + " on attempt " + attempt + ": status stays " + from
                : type + " on attempt " + attempt + ": " + from + " -> " + order.getStatus();
        audit.record(orderId, "DELIVERY_" + type, reason,
                "attempt=" + attempt
                        + ", courier=" + (courier == null ? "none" : courier.getName())
                        + ", pincode=" + order.getPincode()
                        + ", payment=" + order.getPaymentType()
                        + ", failedAttempts=" + order.getAttemptCount());

        // 5. side effects
        Long customerId = order.getCustomer().getId();
        switch (type) {
            case DELIVERED -> {
                trustPoints.recordEvent(customerId, orderId, TrustEventType.DELIVERED);
                if (order.getPaymentType() == PaymentType.PREPAID) {
                    trustPoints.recordEvent(customerId, orderId, TrustEventType.PAID_ONLINE);
                }
                recordPincodeResult(order, true);
            }
            case CUSTOMER_UNREACHABLE, REFUSED, WRONG_ADDRESS -> {
                trustPoints.recordEvent(customerId, orderId, trustEventFor(type));
                recordPincodeResult(order, false);
                rescue.onFailedAttempt(order, type);
            }
            default -> {
                // OUT_FOR_DELIVERY and DELAYED have no side effects
            }
        }
        return order;
    }

    private static int expectedAttempt(Order order, DeliveryEventType type) {
        boolean failure = type == DeliveryEventType.CUSTOMER_UNREACHABLE
                || type == DeliveryEventType.REFUSED
                || type == DeliveryEventType.WRONG_ADDRESS;
        if (failure && order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            return order.getAttemptCount();
        }
        return order.getAttemptCount() + 1;
    }

    private static Set<OrderStatus> allowedFrom(DeliveryEventType type) {
        return switch (type) {
            case OUT_FOR_DELIVERY -> CAN_START_ATTEMPT;
            case DELIVERED, CUSTOMER_UNREACHABLE, REFUSED, WRONG_ADDRESS -> CAN_FINISH_ATTEMPT;
            case DELAYED -> CAN_BE_DELAYED;
        };
    }

    private static TrustEventType trustEventFor(DeliveryEventType failure) {
        return switch (failure) {
            case REFUSED -> TrustEventType.REFUSED;
            case CUSTOMER_UNREACHABLE -> TrustEventType.UNREACHABLE;
            case WRONG_ADDRESS -> TrustEventType.FAKE_ADDRESS;
            default -> throw new IllegalArgumentException("Not a failure event: " + failure);
        };
    }

    /** Some journeys have no courier yet. Then there is nothing to learn for a courier, so we skip it. */
    private void recordPincodeResult(Order order, boolean success) {
        Courier courier = order.getCourier();
        if (courier == null || order.getPincode() == null) {
            log.info("Pincode statistics skipped for order {}: no courier or pincode", order.getId());
            return;
        }
        pincodeStats.recordResult(courier.getId(), order.getPincode(), success);
    }
}
