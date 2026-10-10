package com.rtoshield.delivery;

import com.rtoshield.domain.DeliveryEventType;
import jakarta.validation.constraints.NotNull;

/** What a courier sends us. attemptNo is optional: if missing, the service uses order.attemptCount. */
public record CourierEventRequest(
        @NotNull Long orderId,
        @NotNull DeliveryEventType status,
        Integer attemptNo) {
}
