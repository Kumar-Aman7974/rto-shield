package com.rtoshield.domain;

import java.math.BigDecimal;

/** Output of the journey router: what to do with the order and why. courierId may be null. */
public record DecisionOutcome(Journey journey,
                              Long courierId,
                              BigDecimal expectedLoss,
                              BigDecimal actionCost,
                              String reason) {
}
