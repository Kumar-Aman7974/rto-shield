package com.rtoshield.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin API for settings. Person B protects /config/** with a JWT in M10. */
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigAdminService admin;

    public record ConfigUpdateRequest(@NotBlank String value) {
    }

    @GetMapping
    public List<ConfigView> list() {
        return admin.list();
    }

    @GetMapping("/{key}")
    public ConfigView get(@PathVariable String key) {
        return admin.get(key);
    }

    @PutMapping("/{key}")
    public ConfigView update(@PathVariable String key, @Valid @RequestBody ConfigUpdateRequest request) {
        return admin.update(key, request.value());
    }

    /** Puts every setting back to its default (handy between demo rehearsals). */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settingsReset", admin.resetAll());
        return out;
    }
}
