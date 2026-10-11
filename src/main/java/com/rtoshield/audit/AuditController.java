package com.rtoshield.audit;

import com.rtoshield.domain.AuditLog;
import com.rtoshield.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only view of the audit log.
 *  GET /audit               the latest decisions, newest first
 *  GET /audit?orderId=12    everything decided about one order, oldest first
 *  limit: 1 to 200, default 50
 */
@RestController
@RequiredArgsConstructor
public class AuditController {

    private static final int MAX_LIMIT = 200;

    private final AuditLogRepository repo;

    public record AuditView(Long id, Long orderId, String decision, String reason, String inputs,
                            LocalDateTime createdAt) {
    }

    @GetMapping("/audit")
    public List<AuditView> list(@RequestParam(required = false) Long orderId,
                                @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        List<AuditLog> rows = orderId != null
                ? repo.findByOrderIdOrderByCreatedAtAsc(orderId).stream().limit(limit).toList()
                : repo.findAllByOrderByIdDesc(PageRequest.of(0, limit));
        return rows.stream()
                .map(r -> new AuditView(r.getId(), r.getOrderId(), r.getDecision(), r.getReason(), r.getInputs(), r.getCreatedAt()))
                .toList();
    }
}
