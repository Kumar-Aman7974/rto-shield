package com.rtoshield.recovery;

import com.rtoshield.audit.AuditService;
import com.rtoshield.confirmation.NotificationSender;
import com.rtoshield.domain.ActionType;
import com.rtoshield.domain.DeliveryEventType;
import com.rtoshield.domain.InventoryAction;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.OrderStatus;
import com.rtoshield.domain.RtoReason;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.routing.ProfitCalculator;
import com.rtoshield.scoring.PincodeStatsService;
import com.rtoshield.trust.TrustPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * STUB (M7, Person B). The fields below are the contracts you will call.
 * TODO(B):
 *  attempt 1 fails -> send a reschedule link (POST /recovery/{id}/reschedule is the "link")
 *  attempt 2 fails -> offer pickup point OR a pay-online link (mock endpoint marks it paid)
 *  attempt 3 fails -> markRto: status RTO, RtoReason, trust deduction (once), inventory decision
 *                     (RESTOCK / RESELL_DISCOUNTED / LIQUIDATE based on reason and product margin)
 *  choose the rescue action with profitCalculator.chooseRescueAction(order); do NOT copy its maths.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RescueService {

    private final ProfitCalculator profitCalculator;
    private final TrustPointsService trustPoints;
    private final PincodeStatsService pincodeStats;
    private final NotificationSender sender;
    private final AuditService audit;

    public void onFailedAttempt(Order order, DeliveryEventType failure) {
        // order.status and order.attemptCount have been updated by DeliveryStateService before this call
        int attempts = order.getAttemptCount();
        RtoReason reason = mapFailureToRtoReason(failure);
        log.info("Rescue for order {}: failure={}, attempts={}, status={}", order.getId(), failure, attempts, order.getStatus());

        if (attempts >= 3) {
            // third failed attempt -> mark RTO
            markRto(order, reason);
            return;
        }

        // attempts 1 or 2: choose a rescue action and set the order to RESCHEDULED
        ActionType action = profitCalculator.chooseRescueAction(order);

        // set state to RESCHEDULED so a new attempt can be scheduled by courier/simulator
        order.setStatus(OrderStatus.RESCHEDULED);

        String message;
        switch (action) {
            case WHATSAPP -> message = "Please reschedule your delivery for order " + order.getId() + ". Reply with a new date or visit: POST /recovery/" + order.getId() + "/reschedule";
            case IVR_CALL -> message = "We will call you to reschedule delivery for order " + order.getId() + ".";
            case MANUAL_CALL -> message = "Order " + order.getId() + " requires manual review and a customer call will follow.";
            default -> message = "Please reschedule your delivery for order " + order.getId() + ".";
        }

        // send the chosen notification (mock) and write an audit row describing the action
        sender.send(order, "WHATSAPP", message);
        audit.record(order.getId(), "RESCUE_ACTION", "Attempt " + attempts + " rescue action: " + action,
                "attempts=" + attempts + ", action=" + action + ", reason=" + reason);
        log.info("Rescue action for order {}: action={}, reason={}", order.getId(), action, reason);
    }

    public void markRto(Order order, RtoReason reason) {
        if (order.getStatus() == OrderStatus.RTO) {
            log.info("Order {} already RTO", order.getId());
            return;
        }
        order.setStatus(OrderStatus.RTO);
        order.setRtoReason(reason);
        // simple inventory decision: RESTOCK for unreachable/refused, LIQUIDATE for fake address (example)
        InventoryAction ia = switch (reason) {
            case REFUSED, UNREACHABLE -> InventoryAction.RESTOCK;
            case FAKE_ADDRESS -> InventoryAction.LIQUIDATE;
            case COURIER_DELAY -> InventoryAction.RESELL_DISCOUNTED;
        };
        order.setInventoryAction(ia);

        // record an extra trust event for the ultimate RTO (safe duplicate handling inside TrustPointsService)
        TrustEventType t = switch (reason) {
            case REFUSED -> TrustEventType.REFUSED;
            case UNREACHABLE -> TrustEventType.UNREACHABLE;
            case FAKE_ADDRESS -> TrustEventType.FAKE_ADDRESS;
            default -> null;
        };
        if (t != null) {
            try {
                trustPoints.recordEvent(order.getCustomer().getId(), order.getId(), t);
            } catch (Exception ex) {
                // trust failures must not prevent the RTO state from being set; just log
                log.warn("Failed to record trust event for RTO on order {}: {}", order.getId(), ex.getMessage());
            }
        }

        audit.record(order.getId(), "RESCUE_RTO", "Order marked RTO due to " + reason,
                "rtoReason=" + reason + ", inventoryAction=" + ia);
        sender.send(order, "WHATSAPP", "Order " + order.getId() + " marked RTO due to " + reason + ". We will handle inventory: " + ia);
        log.info("Order {} marked RTO (reason={}, inventory={})", order.getId(), reason, ia);
    }

    private RtoReason mapFailureToRtoReason(DeliveryEventType failure) {
        return switch (failure) {
            case REFUSED -> RtoReason.REFUSED;
            case CUSTOMER_UNREACHABLE -> RtoReason.UNREACHABLE;
            case WRONG_ADDRESS -> RtoReason.FAKE_ADDRESS;
            default -> RtoReason.COURIER_DELAY;
        };
    }}
