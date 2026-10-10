package com.rtoshield.domain;

import java.util.List;

/** Output of the order-health scorer. score is 0-100 (higher = healthier order). */
public record ScoreResult(int score, RiskLevel riskLevel, List<String> reasons) {
}
