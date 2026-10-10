package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private String decision;

    @Column(length = 2000)
    private String reason;

    /** The inputs the decision used, e.g. "score=42, trust=-20, margin=18%". */
    @Column(length = 2000)
    private String inputs;

    private LocalDateTime createdAt = LocalDateTime.now();
}
