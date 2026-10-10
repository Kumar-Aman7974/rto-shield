package com.rtoshield.trust;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.CustomerTier;
import com.rtoshield.domain.TrustEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Contract (M8). Person B calls recordEvent(...) when a delivery or recovery result happens. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrustPointsService {

    private final ConfigService config;

    /**
     * STUB (M8, Person A). Only logs for now.
     * TODO(A): in ONE @Transactional method: skip if the (orderId + eventType) row already exists
     * (so a repeated event counts once), insert a TrustLedgerEntry, update customer.trustBalance,
     * recalculate customer.tier. Point value = config.getInt(eventType.configKey()).
     */
    public void recordEvent(Long customerId, Long orderId, TrustEventType eventType) {
        log.info("STUB trust event: customer={}, order={}, event={} (not stored yet)", customerId, orderId, eventType);
    }

    /** DONE: which tier a balance belongs to. */
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
