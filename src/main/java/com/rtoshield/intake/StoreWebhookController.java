package com.rtoshield.intake;

import com.rtoshield.intake.dto.OrderResponse;
import com.rtoshield.intake.dto.StoreOrderPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Simulated store integration: Postman plays the store. */
@RestController
@RequiredArgsConstructor
public class StoreWebhookController {

    private final OrderService orderService;

    @PostMapping("/webhooks/store/order-created")
    public ResponseEntity<OrderResponse> orderCreated(@Valid @RequestBody StoreOrderPayload payload) {
        OrderResponse body = OrderResponse.from(orderService.createFromStore(payload));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
