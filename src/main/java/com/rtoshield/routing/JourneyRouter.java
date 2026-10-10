package com.rtoshield.routing;

import com.rtoshield.domain.DecisionOutcome;
import com.rtoshield.domain.Journey;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.ScoreResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * STUB (M4, Person A). Always picks OTP_CONFIRM so the pipeline runs.
 * TODO(A): 1) trust tier gate first (PREPAID_ONLY = no pure COD, MANUAL_REVIEW = HUMAN_REVIEW),
 * 2) then score / risk level, 3) then cheapest action that pays for itself (ProfitCalculator),
 * 4) pick a courier by pincode success rate. Always fill "reason" in plain words.
 */
@Service
public class JourneyRouter {

    public DecisionOutcome decide(Order order, ScoreResult score) {
        return new DecisionOutcome(Journey.OTP_CONFIRM, null, BigDecimal.ZERO, BigDecimal.ZERO,
                "STUB: router not written yet (M4, Person A)");
    }
}
