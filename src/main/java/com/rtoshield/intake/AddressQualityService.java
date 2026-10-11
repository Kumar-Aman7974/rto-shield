package com.rtoshield.intake;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Gives an address a 0-100 quality score, based on the address TEXT (that is what couriers actually need).
 * The phone number and city are already checked by the API (10 digits, not blank), so they add no points here.
 *
 *   valid 6-digit pincode            +10
 *   text length 25+ / 15+ / 8+       +45 / +30 / +15
 *   has a number (house / flat no.)  +20
 *   has a locality word (flat, block, sector, near, road, nagar ...)  +15
 *   four or more words               +10
 */
@Service
public class AddressQualityService {

    private static final Pattern DIGIT = Pattern.compile("\\d");

    private static final List<Pattern> LOCALITY_WORDS = Stream.of(
                    "flat", "house", "block", "sector", "near", "opp", "opposite", "road", "street", "lane",
                    "nagar", "colony", "apartment", "apartments", "society", "floor", "village", "market",
                    "chowk", "marg", "bazar", "bazaar", "tower", "building")
            .map(word -> Pattern.compile("\\b" + word + "\\b"))
            .toList();

    public int score(String addressLine, String city, String pincode, String phone) {
        int score = 0;
        if (pincode != null && pincode.matches("\\d{6}")) {
            score += 10;
        }
        if (addressLine == null) {
            return score;
        }
        String text = addressLine.trim();
        int length = text.length();
        if (length >= 25) {
            score += 45;
        } else if (length >= 15) {
            score += 30;
        } else if (length >= 8) {
            score += 15;
        }
        if (DIGIT.matcher(text).find()) {
            score += 20;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (LOCALITY_WORDS.stream().anyMatch(word -> word.matcher(lower).find())) {
            score += 15;
        }
        if (text.split("\\s+").length >= 4) {
            score += 10;
        }
        return Math.min(score, 100);
    }
}
