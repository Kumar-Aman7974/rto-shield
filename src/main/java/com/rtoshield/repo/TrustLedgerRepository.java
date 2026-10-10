package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrustLedgerRepository extends JpaRepository<TrustLedgerEntry, Long> {
    boolean existsByOrderIdAndEventType(Long orderId, TrustEventType eventType);

    List<TrustLedgerEntry> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
