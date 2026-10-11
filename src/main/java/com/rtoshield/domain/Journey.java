package com.rtoshield.domain;

public enum Journey {
    FULL_COD, OTP_CONFIRM, PARTIAL_COD, PREPAID_INCENTIVE, ADDRESS_VERIFY, HUMAN_REVIEW,
    /** The customer already paid online: no cash risk, ship directly. */
    PREPAID_SHIP
}
