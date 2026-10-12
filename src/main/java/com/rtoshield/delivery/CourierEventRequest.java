package com.rtoshield.delivery;

import com.rtoshield.domain.DeliveryEventType;
import jakarta.validation.constraints.NotNull;

/**
 * What a courier sends us.
 * attemptNo is optional. Leave it out and the service works out the attempt itself (failed attempts so far + 1).
 * Send it when a webhook can be retried: the same (order + attemptNo + status) is counted only once.
 * A wrong attemptNo (for example 2 on a first delivery) is rejected with 409.
 */
public record CourierEventRequest(
        @NotNull Long orderId,
        @NotNull DeliveryEventType status,
        Integer attemptNo) {
}
