package com.rtoshield.delivery;

import com.rtoshield.domain.Order;
import com.rtoshield.intake.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * STUB (M6, Person B). Returns the order unchanged so the endpoint works.
 * TODO(B): 1) ignore a repeated event (DeliveryEventRepository.existsByOrderIdAndAttemptNoAndStatus),
 * 2) block impossible moves (cannot reschedule a DELIVERED order), 3) save a DeliveryEvent,
 * 4) update order.status, attemptCount, lastEventAt, 5) on DELIVERED: trust DELIVERED (+ PAID_ONLINE if prepaid)
 * and PincodeStatsService.recordResult(courierId, pincode, true), 6) on a failure: RescueService.onFailedAttempt.
 * Calls you will need: TrustPointsService.recordEvent, PincodeStatsService.recordResult, AuditService.record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryStateService {

    private final OrderService orderService;

    public Order handle(CourierEventRequest request) {
        log.info("STUB courier event: order={}, status={}", request.orderId(), request.status());
        return orderService.get(request.orderId());
    }
}
