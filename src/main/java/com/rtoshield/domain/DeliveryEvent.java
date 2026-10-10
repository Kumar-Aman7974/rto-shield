package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "attempt_no", "status"}))
@Getter
@Setter
@NoArgsConstructor
public class DeliveryEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "attempt_no")
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryEventType status;

    private LocalDateTime createdAt = LocalDateTime.now();
}
