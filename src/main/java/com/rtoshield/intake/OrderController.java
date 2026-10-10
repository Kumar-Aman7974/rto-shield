package com.rtoshield.intake;

import com.rtoshield.intake.dto.CreateOrderRequest;
import com.rtoshield.intake.dto.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse body = OrderResponse.from(orderService.createAndProcess(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(orderService.get(id));
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.list().stream().map(OrderResponse::from).toList();
    }
}
