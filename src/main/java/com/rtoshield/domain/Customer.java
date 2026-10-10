package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String phone;

    private String email;

    /** Running total of trust points. Do not edit directly: go through TrustPointsService. */
    private int trustBalance = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerTier tier = CustomerTier.GOOD;

    /** 0 means a first-time customer. */
    private int totalOrders = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}
