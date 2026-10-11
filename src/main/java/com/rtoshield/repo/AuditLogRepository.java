package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    /** Newest first. The page size is the limit. */
    List<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
