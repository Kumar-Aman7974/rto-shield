package com.rtoshield.domain;

public enum TrustEventType {
    DELIVERED, PAID_ONLINE, CONFIRMED_QUICKLY, REFUSED, UNREACHABLE, LATE_CANCEL, FAKE_ADDRESS;

    /** Config key that holds the point value for this event, e.g. "trust.points.refused". */
    public String configKey() {
        return "trust.points." + name().toLowerCase();
    }
}
