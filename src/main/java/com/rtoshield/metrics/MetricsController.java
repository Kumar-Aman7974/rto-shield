package com.rtoshield.metrics;

import com.rtoshield.domain.OrderStatus;
import com.rtoshield.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STUB (M11b, Person B). Only simple counts for now.
 * TODO(B): summary (realised revenue, expected margin, recovery value), RTO trend per day,
 * top-5 risky pincodes / products / channels, RTO reasons breakdown. Feed dashboard.html (Chart.js).
 */
@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final OrderRepository orders;

    @GetMapping("/metrics/summary")
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalOrders", orders.count());
        out.put("delivered", orders.findByStatus(OrderStatus.DELIVERED).size());
        out.put("rto", orders.findByStatus(OrderStatus.RTO).size());
        out.put("note", "STUB: revenue, margin and recovery value come in M11b (Person B)");
        return out;
    }
}
