package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity = 1;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    private CampaignSource campaignSource = CampaignSource.ORGANIC;

    @Column(length = 10)
    private String phone;

    private String addressLine;

    private String city;

    @Column(length = 6)
    private String pincode;

    /** 0-100, set by AddressQualityService. */
    private int addressQuality;

    @Column(precision = 12, scale = 2)
    private BigDecimal orderValue;

    // ---- written by the pipeline (OrderService) ----
    private Integer healthScore;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /** Reasons joined with " | ". */
    @Column(length = 2000)
    private String scoreReasons;

    @Enumerated(EnumType.STRING)
    private Journey journey;

    @Column(length = 2000)
    private String decisionReason;

    @ManyToOne
    @JoinColumn(name = "courier_id")
    private Courier courier;

    // ---- lifecycle (confirmation / delivery / recovery) ----
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;

    private int attemptCount = 0;

    @Enumerated(EnumType.STRING)
    private RtoReason rtoReason;

    @Enumerated(EnumType.STRING)
    private InventoryAction inventoryAction;

    private String otpCode;

    private LocalDateTime otpExpiresAt;

    private LocalDateTime reminderSentAt;

    /** true for orders created by the simulator. */
    private boolean simulated = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** Time of the last status change or courier event. The delay watcher reads this. */
    private LocalDateTime lastEventAt = LocalDateTime.now();
}
