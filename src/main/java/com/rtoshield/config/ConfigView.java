package com.rtoshield.config;

/** One setting as the config API shows it. */
public record ConfigView(String key, String value, String defaultValue, boolean changed, String allowed) {
}
