package com.rtoshield.demo;

import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.CampaignSource;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.PaymentType;
import com.rtoshield.domain.Product;
import com.rtoshield.intake.OrderService;
import com.rtoshield.intake.dto.CreateOrderRequest;
import com.rtoshield.intake.dto.OrderResponse;
import com.rtoshield.repo.CustomerRepository;
import com.rtoshield.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smoke test AND the future one-button demo. Right now only step 1 runs for real.
 * Run it after every merge: if it errors, the pipeline is broken.
 * TODO(B): add steps 2-5 as the modules appear (confirm reply, courier NDR event, recovery, before/after margin).
 */
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final OrderService orderService;
    private final CustomerRepository customers;
    private final ProductRepository products;

    @PostMapping("/demo/run")
    public Map<String, Object> run() {
        Customer firstTimer = customers.findByPhone("9876500002")
                .orElseThrow(() -> new NotFoundException("Seed customer 9876500002 missing"));
        Product watch = products.findByName("Smart Watch")
                .orElseThrow(() -> new NotFoundException("Seed product 'Smart Watch' missing"));

        CreateOrderRequest request = new CreateOrderRequest(
                firstTimer.getId(), watch.getId(), 1, PaymentType.COD, CampaignSource.ADS,
                "Flat 12, Block C, Dwarka Sector 7", "New Delhi", "110059", "9876500002");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("step1_order_scored_and_routed", OrderResponse.from(orderService.createAndProcess(request)));
        out.put("step2_confirmation", "NOT_IMPLEMENTED (M5, Person B)");
        out.put("step3_delivery_risk", "NOT_IMPLEMENTED (M6, Person B)");
        out.put("step4_recovery_action", "NOT_IMPLEMENTED (M7, Person B)");
        out.put("step5_margin_before_after", "NOT_IMPLEMENTED (M11, both)");
        return out;
    }
}
