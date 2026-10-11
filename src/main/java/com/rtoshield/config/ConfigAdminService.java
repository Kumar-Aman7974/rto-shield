package com.rtoshield.config;

import com.rtoshield.audit.AuditService;
import com.rtoshield.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets an admin read and change settings while the app runs. Every change is validated first (see ConfigRules)
 * and writes one audit row, so "who changed what, from what to what" is always on record.
 * Changes take effect on the very next order: ConfigService reads the database on every call.
 */
@Service
@RequiredArgsConstructor
public class ConfigAdminService {

    private final ConfigService config;
    private final AuditService audit;

    public List<ConfigView> list() {
        return ConfigKeys.DEFAULTS.keySet().stream().map(this::view).toList();
    }

    public ConfigView get(String key) {
        requireKnown(key);
        return view(key);
    }

    @Transactional
    public ConfigView update(String key, String rawValue) {
        requireKnown(key);
        String oldValue = config.get(key);
        String newValue = ConfigRules.validate(key, rawValue, config::get);
        if (sameNumber(oldValue, newValue)) {
            return view(key);
        }
        config.set(key, newValue);
        audit.record(null, "CONFIG_CHANGE",
                key + ": " + oldValue + " -> " + newValue,
                "key=" + key + ", old=" + oldValue + ", new=" + newValue);
        return view(key);
    }

    /**
     * Puts every setting back to its default. Returns how many settings were different.
     * The ordering checks are skipped on purpose: the end result (all defaults) is always consistent.
     */
    @Transactional
    public int resetAll() {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : ConfigKeys.DEFAULTS.entrySet()) {
            if (!sameNumber(config.get(entry.getKey()), entry.getValue())) {
                config.set(entry.getKey(), entry.getValue());
                changed.add(entry.getKey());
            }
        }
        if (!changed.isEmpty()) {
            audit.record(null, "CONFIG_RESET",
                    changed.size() + " setting(s) reset to defaults: " + String.join(", ", changed),
                    "keys=" + String.join(",", changed));
        }
        return changed.size();
    }

    private ConfigView view(String key) {
        String value = config.get(key);
        String defaultValue = ConfigKeys.DEFAULTS.get(key);
        return new ConfigView(key, value, defaultValue, !sameNumber(value, defaultValue), ConfigRules.describe(key));
    }

    private void requireKnown(String key) {
        if (!ConfigKeys.DEFAULTS.containsKey(key)) {
            throw new NotFoundException("Unknown config key: " + key);
        }
    }

    private static boolean sameNumber(String a, String b) {
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }
}
