package com.rtoshield.confirmation;

import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.DecisionOutcome;
import com.rtoshield.domain.Journey;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.OrderStatus;
import com.rtoshield.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRE_MINUTES = 15;

    /** Called by OrderService inside its transaction; changes to "order" are saved automatically. */
    public void execute(Order order, DecisionOutcome decision) {
        Journey j = decision.journey();
        if (j == Journey.FULL_COD || j == Journey.PREPAID_SHIP) {
            order.setStatus(OrderStatus.CONFIRMED);
            return;
        }

        switch (j) {
            case HUMAN_REVIEW -> {
                order.setStatus(OrderStatus.HUMAN_REVIEW);
                sender.send(order, "WHATSAPP", "Order " + order.getId() + " requires human review. We will contact you.");
            }
            case OTP_CONFIRM -> {
                String otp = String.format("%0" + OTP_LENGTH + "d", ThreadLocalRandom.current().nextInt(0, (int) Math.pow(10, OTP_LENGTH)));
                order.setOtpCode(otp);
                order.setOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES));
                order.setStatus(OrderStatus.AWAITING_CONFIRMATION);
                sender.send(order, "WHATSAPP", "Your OTP for order " + order.getId() + " is: " + otp + " (expires in " + OTP_EXPIRE_MINUTES + " minutes)");
            }
            case PARTIAL_COD -> {
                order.setStatus(OrderStatus.AWAITING_CONFIRMATION);
                sender.send(order, "WHATSAPP", "Please pay a small advance to confirm your order " + order.getId() + ". Pay here: https://pay.mock/" + order.getId());
            }
            case PREPAID_INCENTIVE -> {
                order.setStatus(OrderStatus.AWAITING_CONFIRMATION);
                sender.send(order, "WHATSAPP", "Prepay now for a discount on order " + order.getId() + ". Reply YES to accept the offer.");
            }
            case ADDRESS_VERIFY -> {
                order.setStatus(OrderStatus.AWAITING_CONFIRMATION);
                sender.send(order, "WHATSAPP", "Please confirm your address for order " + order.getId() + ":\n" + compactAddress(order) + "\nReply YES to confirm or send corrected address.");
            }
            default -> {
                order.setStatus(OrderStatus.AWAITING_CONFIRMATION);
                sender.send(order, "WHATSAPP", "Please respond YES to confirm order " + order.getId());
            }

        }
    }

    private String compactAddress(Order order) {
        StringBuilder sb = new StringBuilder();
        if (order.getAddressLine() != null) sb.append(order.getAddressLine());
        if (order.getCity() != null) sb.append(" ").append(order.getCity());
        if (order.getPincode() != null) sb.append(" ").append(order.getPincode());
        return sb.toString().trim();
    }

    /** Called by the WhatsApp reply webhook. Returns the order's status after the reply. */
    @Transactional
    public OrderStatus handleReply(Long orderId, String text) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION) {
            log.info("Reply ignored for order {}: not awaiting confirmation (status={})", orderId, order.getStatus());
            return order.getStatus();
        }

        String t = text == null ? "" : text.trim();

        // NO -> cancel
        if (t.equalsIgnoreCase("NO")) {
            order.setStatus(OrderStatus.CANCELLED);
            orders.save(order);
            sender.send(order, "WHATSAPP", "Your order " + order.getId() + " has been cancelled as requested.");
            return OrderStatus.CANCELLED;
        }

        // YES -> confirm and ship
        if (t.equalsIgnoreCase("YES")) {
            order.setStatus(OrderStatus.CONFIRMED);
            // Simulate immediate shipping after confirmation
            order.setStatus(OrderStatus.SHIPPED);
            orders.save(order);
            sender.send(order, "WHATSAPP", "Thank you — your order " + order.getId() + " is confirmed and shipped.");
            return OrderStatus.SHIPPED;
        }

        // OTP path: if an OTP was set, accept the 6-digit code
        if (order.getOtpCode() != null && t.matches("\\d{" + OTP_LENGTH + "}")) {
            if (order.getOtpCode().equals(t) && order.getOtpExpiresAt() != null && LocalDateTime.now().isBefore(order.getOtpExpiresAt())) {
                order.setOtpCode(null);
                order.setOtpExpiresAt(null);
                order.setStatus(OrderStatus.CONFIRMED);
                order.setStatus(OrderStatus.SHIPPED);
                orders.save(order);
                sender.send(order, "WHATSAPP", "OTP accepted — order " + order.getId() + " confirmed and shipped.");
                return OrderStatus.SHIPPED;
            } else {
                // wrong or expired OTP: leave order awaiting confirmation
                sender.send(order, "WHATSAPP", "Incorrect or expired OTP for order " + order.getId() + ". Reply the 6-digit code or NO to cancel.");
                return order.getStatus();
            }
        }

        // Freeform text for ADDRESS_VERIFY -> treat as corrected address and confirm
        // If not OTP and not YES/NO, assume user supplied corrected address
        order.setAddressLine(t);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setStatus(OrderStatus.SHIPPED);
        orders.save(order);
        sender.send(order, "WHATSAPP", "Thanks — address updated and order " + order.getId() + " confirmed and shipped.");
        return OrderStatus.SHIPPED;
    }
}
