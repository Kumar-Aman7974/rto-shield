package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courier_pincode_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"courier_id", "pincode"}))
@Getter
@Setter
@NoArgsConstructor
public class CourierPincodeStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "courier_id")
    private Courier courier;

    @Column(nullable = false, length = 6)
    private String pincode;

    private int attempts;

    private int successes;

    public double successRate() {
        return attempts == 0 ? 0.0 : (double) successes / attempts;
    }
}
