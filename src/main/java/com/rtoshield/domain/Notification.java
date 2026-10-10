package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private Long customerId;

    /** WHATSAPP, OTP, IVR, SMS ... plain text on purpose. */
    private String channel;

    @Column(length = 1000)
    private String message;

    private LocalDateTime createdAt = LocalDateTime.now();
}
