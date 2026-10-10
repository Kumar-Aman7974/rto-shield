package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<Notification> findByCustomerIdOrderByCreatedAtAsc(Long customerId);
}
