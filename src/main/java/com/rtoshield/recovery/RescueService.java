package com.rtoshield.recovery;

import com.rtoshield.audit.AuditService;
import com.rtoshield.confirmation.NotificationSender;
import com.rtoshield.domain.DeliveryEventType;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.RtoReason;
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
        log.info("STUB rescue: order={}, failure={}", order.getId(), failure);
    }

    public void markRto(Order order, RtoReason reason) {
        log.info("STUB RTO: order={}, reason={}", order.getId(), reason);
    }
}
