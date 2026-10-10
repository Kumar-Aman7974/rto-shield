package com.rtoshield.delivery;

import com.rtoshield.intake.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Postman plays the courier here. Say "courier APIs mocked" in the demo. */
@RestController
@RequiredArgsConstructor
public class CourierEventController {

    private final DeliveryStateService deliveryStateService;

    @PostMapping("/courier/events")
    public OrderResponse event(@Valid @RequestBody CourierEventRequest request) {
        return OrderResponse.from(deliveryStateService.handle(request));
    }
}
