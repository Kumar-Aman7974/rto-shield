package com.rtoshield.trust;

import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.CustomerTier;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.repo.CustomerRepository;
import com.rtoshield.repo.TrustLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TrustController {

    private final CustomerRepository customers;
    private final TrustLedgerRepository ledger;

    public record TrustEntryView(TrustEventType eventType, int delta, Long orderId, LocalDateTime createdAt) {
    }

    public record TrustSummary(Long customerId, String name, int balance, CustomerTier tier,
                               List<TrustEntryView> history) {
    }

    @GetMapping("/customers/{id}/trust")
    public TrustSummary trust(@PathVariable Long id) {
        Customer c = customers.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
        List<TrustEntryView> history = ledger.findByCustomerIdOrderByCreatedAtDesc(id).stream()
                .map(e -> new TrustEntryView(e.getEventType(), e.getDelta(), e.getOrderId(), e.getCreatedAt()))
                .toList();
        return new TrustSummary(c.getId(), c.getName(), c.getTrustBalance(), c.getTier(), history);
    }
}
