package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trust_ledger",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "event_type"}))
@Getter
@Setter
@NoArgsConstructor
public class TrustLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TrustEventType eventType;

    private int delta;

    private LocalDateTime createdAt = LocalDateTime.now();
}
