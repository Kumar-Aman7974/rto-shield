package com.rtoshield.audit;

import com.rtoshield.domain.AuditLog;
import com.rtoshield.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** One call records one decision. Rows are never updated or deleted. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    public void record(Long orderId, String decision, String reason, String inputs) {
        AuditLog row = new AuditLog();
        row.setOrderId(orderId);
        row.setDecision(decision);
        row.setReason(truncate(reason));
        row.setInputs(truncate(inputs));
        repo.save(row);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 1900 ? text.substring(0, 1900) : text;
    }
}
