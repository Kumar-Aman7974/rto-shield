package com.rtoshield.confirmation;

import com.rtoshield.domain.DecisionOutcome;
import com.rtoshield.domain.Journey;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.OrderStatus;
import com.rtoshield.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * STUB (M5, Person B). One generic executor for ALL six journeys: send a different message, set a status,
 * wait for the customer's reply.
 * TODO(B):
 *  FULL_COD          -> status CONFIRMED, no message
 *  OTP_CONFIRM       -> make a 6-digit otpCode + otpExpiresAt (15 min), send it, status AWAITING_CONFIRMATION
 *  PARTIAL_COD       -> send a mock "pay advance" link, status AWAITING_CONFIRMATION
 *  PREPAID_INCENTIVE -> send a discount-for-prepaying offer, status AWAITING_CONFIRMATION
 *  ADDRESS_VERIFY    -> ask the customer to confirm or correct the address, status AWAITING_CONFIRMATION
 *  HUMAN_REVIEW      -> status HUMAN_REVIEW (plus GET /admin/review-queue and approve/reject)
 * handleReply: "YES"/correct OTP -> CONFIRMED then SHIPPED; "NO" -> CANCELLED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JourneyExecutor {

    private final NotificationSender sender;
    private final OrderRepository orders;

    /** Called by OrderService inside its transaction; changes to "order" are saved automatically. */
    public void execute(Order order, DecisionOutcome decision) {
        order.setStatus(decision.journey() == Journey.FULL_COD
                ? OrderStatus.CONFIRMED
                : OrderStatus.AWAITING_CONFIRMATION);
        sender.send(order, "WHATSAPP",
                "STUB: journey " + decision.journey() + " started for order " + order.getId());
    }

    /** Called by the WhatsApp reply webhook. Returns the order's status after the reply. */
    public OrderStatus handleReply(Long orderId, String text) {
        log.info("STUB reply for order {}: {}", orderId, text);
        return orders.findById(orderId).map(Order::getStatus).orElse(null);
    }
}
