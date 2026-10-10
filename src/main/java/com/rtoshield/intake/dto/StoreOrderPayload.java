package com.rtoshield.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Shopify-style "order created" payload. We define the shape ourselves:
 * this is the simulated store integration.
 */
public record StoreOrderPayload(
        @NotNull @Valid StoreCustomer customer,
        @NotNull @Valid @JsonProperty("shipping_address") StoreAddress shippingAddress,
        @NotEmpty @Valid @JsonProperty("line_items") List<StoreLineItem> lineItems,
        @JsonProperty("payment_gateway_names") List<String> paymentGatewayNames,
        @JsonProperty("source_name") String sourceName) {

    public record StoreCustomer(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "\\d{10}", message = "phone must be 10 digits") String phone,
            String email) {
    }

    public record StoreAddress(
            @NotBlank String address1,
            @NotBlank String city,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "zip must be 6 digits") String zip) {
    }

    public record StoreLineItem(
            @NotNull @JsonProperty("product_id") Long productId,
            @Min(value = 1, message = "quantity must be at least 1") int quantity) {
    }
}
