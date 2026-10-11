package com.rtoshield.intake.dto;

import com.rtoshield.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** What the API returns for an order. Keeps entities out of the JSON. */
public record OrderResponse(
        Long id,
        Long customerId,
        String customerName,
        Long productId,
        String productName,
        BigDecimal orderValue,
        PaymentType paymentType,
        CampaignSource campaignSource,
        int addressQuality,
        OrderStatus status,
        Integer healthScore,
        RiskLevel riskLevel,
        List<String> scoreReasons,
        Journey journey,
        String decisionReason,
        BigDecimal expectedLoss,
        BigDecimal actionCost,
        String courier,
        int attemptCount,
        RtoReason rtoReason,
        InventoryAction inventoryAction,
        LocalDateTime createdAt) {

    public static OrderResponse from(Order o) {
        String raw = o.getScoreReasons();
        List<String> reasons = (raw == null || raw.isBlank())
                ? List.of()
                : Arrays.asList(raw.split("\\s*\\|\\s*"));
        return new OrderResponse(
                o.getId(),
                o.getCustomer().getId(),
                o.getCustomer().getName(),
                o.getProduct().getId(),
                o.getProduct().getName(),
                o.getOrderValue(),
                o.getPaymentType(),
                o.getCampaignSource(),
                o.getAddressQuality(),
                o.getStatus(),
                o.getHealthScore(),
                o.getRiskLevel(),
                reasons,
                o.getJourney(),
                o.getDecisionReason(),
                o.getExpectedLoss(),
                o.getActionCost(),
                o.getCourier() == null ? null : o.getCourier().getName(),
                o.getAttemptCount(),
                o.getRtoReason(),
                o.getInventoryAction(),
                o.getCreatedAt());
    }
}
