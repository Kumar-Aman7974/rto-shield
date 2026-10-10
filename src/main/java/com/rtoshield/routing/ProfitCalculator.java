package com.rtoshield.routing;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.ActionType;
import com.rtoshield.domain.Order;
import com.rtoshield.domain.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The profit-aware core. Person B's RescueService calls this too, so there is only ONE copy of the money maths.
 * expectedLoss = chance of failure x (two-way shipping + packing + damage).
 */
@Service
@RequiredArgsConstructor
public class ProfitCalculator {

    private final ConfigService config;

    /** Chance (0.0 to 1.0) that an order of this risk level fails. Values come from config. */
    public double pFail(RiskLevel risk) {
        return switch (risk) {
            case LOW -> config.getDouble(ConfigKeys.PFAIL_LOW);
            case MEDIUM -> config.getDouble(ConfigKeys.PFAIL_MEDIUM);
            case HIGH -> config.getDouble(ConfigKeys.PFAIL_HIGH);
        };
    }

    /** What we lose in rupees if the order comes back, already weighted by the chance it fails. */
    public BigDecimal expectedLoss(Order order, double pFail) {
        BigDecimal lossIfReturned = config.getDecimal(ConfigKeys.LOSS_SHIPPING_TWO_WAY)
                .add(config.getDecimal(ConfigKeys.LOSS_PACKING))
                .add(config.getDecimal(ConfigKeys.LOSS_DAMAGE));
        return lossIfReturned.multiply(BigDecimal.valueOf(pFail)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Cost in rupees of one action (WhatsApp, IVR call, manual call). */
    public BigDecimal actionCost(ActionType type) {
        return switch (type) {
            case NONE -> BigDecimal.ZERO;
            case WHATSAPP -> config.getDecimal(ConfigKeys.COST_WHATSAPP);
            case IVR_CALL -> config.getDecimal(ConfigKeys.COST_IVR);
            case MANUAL_CALL -> config.getDecimal(ConfigKeys.COST_MANUAL);
        };
    }

    /**
     * STUB (M4/M7, Person A). Returns the cheapest action that pays for itself.
     * TODO(A): compare each action's cost with the expected loss it can prevent; also count the
     * margin lost when genuine customers drop off (config key dropoff.prepaid_rate).
     */
    public ActionType chooseRescueAction(Order order) {
        return ActionType.WHATSAPP;
    }
}
