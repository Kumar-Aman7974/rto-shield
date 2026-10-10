package com.rtoshield.scoring;

import com.rtoshield.domain.Order;
import com.rtoshield.domain.ScoreResult;

/** Contract (M3). Anyone may call it; only Person A changes it. */
public interface OrderHealthScorer {
    ScoreResult score(Order order);
}
