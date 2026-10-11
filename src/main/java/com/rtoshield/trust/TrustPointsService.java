package com.rtoshield.trust;

import com.rtoshield.common.NotFoundException;
import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.CustomerTier;
import com.rtoshield.domain.TrustEventType;
import com.rtoshield.domain.TrustLedgerEntry;
import com.rtoshield.repo.CustomerRepository;
import com.rtoshield.repo.TrustLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M8: trust points. The ledger is append-only (every event is a new row, nothing is edited or deleted).
 * The customer's balance and tier are updated in the SAME transaction as the ledger row,
 * so the balance can never disagree with the ledger.
 *
 * Rules:
 *  - The same (orderId + eventType) counts only once. A repeated event is ignored.
 *  - Different events on one order all count (e.g. UNREACHABLE on attempt 1, then REFUSED on attempt 2).
 *  - If orderId is null, the event is always recorded (used for manual or order-less events).
 *  - Point values come from config (trust.points.*), never hardcoded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrustPointsService {

    private final ConfigService config;
    private final CustomerRepository customers;
    private final TrustLedgerRepository ledger;

    @Transactional
    public void recordEvent(Long customerId, Long orderId, TrustEventType eventType) {
        // lock the customer row first, so two events for the same customer run one after the other
        Customer customer = customers.findByIdForUpdate(customerId)
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found"));

        if (orderId != null && ledger.existsByOrderIdAndEventType(orderId, eventType)) {
            log.info("Trust event ignored (already counted): customer={}, order={}, event={}",
                    customerId, orderId, eventType);
            return;
        }

        int delta = config.getInt(eventType.configKey());
        int newBalance = customer.getTrustBalance() + delta;

        TrustLedgerEntry entry = new TrustLedgerEntry();
        entry.setCustomer(customer);
        entry.setOrderId(orderId);
        entry.setEventType(eventType);
        entry.setDelta(delta);
        ledger.save(entry);

        CustomerTier oldTier = customer.getTier();
        customer.setTrustBalance(newBalance);
        customer.setTier(tierFor(newBalance));
        customers.save(customer);

        log.info("Trust event recorded: customer={}, order={}, event={}, delta={}, balance={}, tier {} -> {}",
                customerId, orderId, eventType, delta, newBalance, oldTier, customer.getTier());
    }

    /** Which tier a balance belongs to. Limits come from config (defaults: -40 and -100). */
    public CustomerTier tierFor(int balance) {
        if (balance < config.getInt(ConfigKeys.TIER_MANUAL_REVIEW_BELOW)) {
            return CustomerTier.MANUAL_REVIEW;
        }
        if (balance < config.getInt(ConfigKeys.TIER_PREPAID_ONLY_BELOW)) {
            return CustomerTier.PREPAID_ONLY;
        }
        if (balance < 0) {
            return CustomerTier.CAUTION;
        }
        return CustomerTier.GOOD;
    }
}
