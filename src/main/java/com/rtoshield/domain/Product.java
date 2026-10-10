package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    /** Margin as a percentage of the selling price, e.g. 55.62. */
    public BigDecimal getMarginPct() {
        if (sellingPrice == null || sellingPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return sellingPrice.subtract(costPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(sellingPrice, 2, java.math.RoundingMode.HALF_UP);
    }
}
