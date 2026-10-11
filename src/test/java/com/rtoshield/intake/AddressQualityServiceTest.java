package com.rtoshield.intake;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The address score is based on the address text only. Before this change a valid pincode, phone and city alone
 * gave 60 points, so the "poor address" rule and the ADDRESS_VERIFY journey could never fire.
 */
class AddressQualityServiceTest {

    private final AddressQualityService service = new AddressQualityService();

    @Test
    void fullAddressScores100() {
        assertThat(service.score("Flat 12, Block C, Dwarka Sector 7", "New Delhi", "110059", "9999999999")).isEqualTo(100);
    }

    @Test
    void onlyACityNameScoresPoorly() {
        assertThat(service.score("Delhi", "New Delhi", "110059", "9999999999")).isEqualTo(10);
    }

    @Test
    void shortAddressWithANumberIsFair() {
        assertThat(service.score("House 5, Patna", "New Delhi", "110059", "9999999999")).isEqualTo(60);
    }

    @Test
    void mediumAddressWithRoadAndNumber() {
        assertThat(service.score("12 MG Road Patna", "New Delhi", "110059", "9999999999")).isEqualTo(85);
    }

    @Test
    void landmarkWithoutANumber() {
        assertThat(service.score("near market", "New Delhi", "110059", "9999999999")).isEqualTo(40);
    }

    @Test
    void blankAddressOnlyGetsThePincodePoints() {
        assertThat(service.score("", "New Delhi", "110059", "9999999999")).isEqualTo(10);
    }

    @Test
    void invalidPincodeLosesTenPoints() {
        assertThat(service.score("Flat 12, Block C, Dwarka Sector 7", "New Delhi", "12345", "9999999999")).isEqualTo(90);
    }

    @Test
    void surroundingSpacesAreIgnored() {
        assertThat(service.score("   Flat 12, Block C, Dwarka Sector 7   ", "New Delhi", "110059", "9999999999")).isEqualTo(100);
    }

    @Test
    void upperCaseIsFine() {
        assertThat(service.score("FLAT 12 BLOCK C DWARKA SECTOR 7", "New Delhi", "110059", "9999999999")).isEqualTo(100);
    }

    @Test
    void lineBreaksAreFine() {
        assertThat(service.score("Plot 7\nSector 9\nNoida Uttar Pradesh", "New Delhi", "201301", "9999999999")).isEqualTo(100);
    }

    @Test
    void localityWordsMustBeWholeWords() {
        assertThat(service.score("flatiron", "New Delhi", "110059", "9999999999")).isEqualTo(25);
    }

    @Test
    void missingAddressOnlyGetsThePincodePoints() {
        assertThat(service.score(null, "New Delhi", "110059", "9999999999")).isEqualTo(10);
    }

    @Test
    void aValidRequestCanNowReachThePoorAddressBand() {
        // "Delhi" with valid pincode, city and phone: this used to score 60 and could never be "poor" (below 40)
        assertThat(service.score("Delhi", "New Delhi", "110059", "9999999999")).isLessThan(40);
    }
}
