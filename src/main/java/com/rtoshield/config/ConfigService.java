package com.rtoshield.config;

import com.rtoshield.domain.ConfigEntry;
import com.rtoshield.repo.ConfigEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Reads a setting from the database first, then falls back to the default in ConfigKeys. */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigEntryRepository repo;

    public String get(String key) {
        String value = repo.findById(key)
                .map(ConfigEntry::getValue)
                .orElse(ConfigKeys.DEFAULTS.get(key));
        if (value == null) {
            throw new IllegalArgumentException("Unknown config key: " + key);
        }
        return value.trim();
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(get(key));
    }

    /** Used by the config API (M9). Creates the key if it does not exist yet. */
    public void set(String key, String value) {
        if (!ConfigKeys.DEFAULTS.containsKey(key) && !repo.existsById(key)) {
            throw new IllegalArgumentException("Unknown config key: " + key);
        }
        ConfigEntry entry = repo.findById(key).orElseGet(() -> new ConfigEntry(key, value, "custom"));
        entry.setValue(value);
        repo.save(entry);
    }
}
