package com.rtoshield.trust;

import com.rtoshield.common.NotFoundException;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.CustomerTier;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.repo.CustomerRepository;
import com.rtoshield.repo.TrustLedgerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TrustController {

    private final CustomerRepository customers;
    private final TrustLedgerRepository ledger;
    private final TrustPointsService trustPoints;

    public record TrustEntryView(TrustEventType eventType, int delta, Long orderId, LocalDateTime createdAt) {
    }

    public record TrustSummary(Long customerId, String name, int balance, CustomerTier tier,
                               List<TrustEntryView> history) {
    }

    public record TrustEventRequest(@NotNull TrustEventType eventType, Long orderId) {
    }

    /** Live demo view: balance, tier and the full ledger, newest first. */
    @GetMapping("/customers/{id}/trust")
    public TrustSummary trust(@PathVariable Long id) {
        Customer c = customers.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
        List<TrustEntryView> history = ledger.findByCustomerIdOrderByCreatedAtDesc(id).stream()
                .map(e -> new TrustEntryView(e.getEventType(), e.getDelta(), e.getOrderId(), e.getCreatedAt()))
                .toList();
        return new TrustSummary(c.getId(), c.getName(), c.getTrustBalance(), c.getTier(), history);
    }

    /**
     * Test/admin endpoint: applies one trust event, the same way the delivery code does.
     * Used in Swagger to check M8 before the delivery module exists, and to show the "learning loop" in the demo.
     */
    @PostMapping("/customers/{id}/trust/events")
    public TrustSummary applyEvent(@PathVariable Long id, @Valid @RequestBody TrustEventRequest request) {
        trustPoints.recordEvent(id, request.orderId(), request.eventType());
        return trust(id);
    }
}
