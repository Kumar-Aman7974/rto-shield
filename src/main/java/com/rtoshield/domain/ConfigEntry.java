package com.rtoshield.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "config_entries")
@Getter
@Setter
@NoArgsConstructor
public class ConfigEntry {
    @Id
    @Column(name = "config_key")
    private String key;

    @Column(name = "config_value", nullable = false)
    private String value;

    private String description;

    public ConfigEntry(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }
}
