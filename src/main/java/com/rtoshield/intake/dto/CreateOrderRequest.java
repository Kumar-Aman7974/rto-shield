package com.rtoshield.intake.dto;

import com.rtoshield.domain.CampaignSource;
import com.rtoshield.domain.PaymentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Our own order shape. Used by POST /orders, the store webhook and the simulator. */
public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotNull Long productId,
        @Min(value = 1, message = "quantity must be at least 1") int quantity,
        @NotNull PaymentType paymentType,
        CampaignSource campaignSource,
        @NotBlank String addressLine,
        @NotBlank String city,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "pincode must be 6 digits") String pincode,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "phone must be 10 digits") String phone) {
}
