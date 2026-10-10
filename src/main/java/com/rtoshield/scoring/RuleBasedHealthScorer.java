package com.rtoshield.scoring;

import com.rtoshield.domain.Order;
import com.rtoshield.domain.RiskLevel;
import com.rtoshield.domain.ScoreResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * STUB (M3, Person A). Returns a fixed score so the pipeline runs end to end.
 * TODO(A): add one rule at a time, each adding a reason string when it fires:
 * address quality, margin, campaign source, payment type, pincode refusal rate,
 * trust points, courier success for the pincode, odd hour, repeat phone in 24h.
 */
@Service
public class RuleBasedHealthScorer implements OrderHealthScorer {

    @Override
    public ScoreResult score(Order order) {
        return new ScoreResult(50, RiskLevel.MEDIUM,
                List.of("STUB: scoring rules not written yet (M3, Person A)"));
    }
}
