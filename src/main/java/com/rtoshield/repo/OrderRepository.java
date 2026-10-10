package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime since);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerId(Long customerId);

    /** Used by the delay watcher: orders still in these statuses with no event since "before". */
    List<Order> findByStatusInAndLastEventAtBefore(Collection<OrderStatus> statuses, LocalDateTime before);
}
