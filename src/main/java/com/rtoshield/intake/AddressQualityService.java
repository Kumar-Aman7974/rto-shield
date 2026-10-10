package com.rtoshield.intake;

import org.springframework.stereotype.Service;

/** Gives an address a 0-100 quality score. Simple on purpose; Person A can sharpen it. */
@Service
public class AddressQualityService {

    public int score(String addressLine, String city, String pincode, String phone) {
        int score = 0;
        if (pincode != null && pincode.matches("\\d{6}")) {
            score += 30;
        }
        if (phone != null && phone.matches("\\d{10}")) {
            score += 20;
        }
        if (city != null && !city.isBlank()) {
            score += 10;
        }
        if (addressLine != null) {
            String a = addressLine.trim();
            if (a.length() >= 20) {
                score += 30;
            } else if (a.length() >= 10) {
                score += 15;
            }
            if (a.matches(".*\\d.*")) {
                score += 10;   // has a house/flat number
            }
        }
        return Math.min(score, 100);
    }
}
