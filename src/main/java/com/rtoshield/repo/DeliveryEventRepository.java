package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {
    boolean existsByOrderIdAndAttemptNoAndStatus(Long orderId, int attemptNo, DeliveryEventType status);
}
