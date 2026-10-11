package com.rtoshield.routing;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.Courier;
import com.rtoshield.domain.Order;
import com.rtoshield.repo.CourierRepository;
import com.rtoshield.scoring.PincodeStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the courier with the lowest EXPECTED cost for this order in this pincode:
 *   expected cost = shipping + (1 - success rate) x (return shipping + packing + damage + the margin we lose)
 * So a reliable courier wins on valuable orders, and a cheap courier can win on cheap orders.
 * Ties go to the courier with the higher success rate, then to the lower id.
 */
@Service
@RequiredArgsConstructor
public class CourierRecommender {

    private final CourierRepository couriers;
    private final PincodeStatsService pincodeStats;
    private final ProfitCalculator profit;
    private final ConfigService config;

    public record CourierChoice(Courier courier, BigDecimal expectedCost, double successRate) {
    }

    public Optional<CourierChoice> recommend(Order order) {
        List<Courier> all = couriers.findAll().stream()
                .sorted(Comparator.comparing(Courier::getId))
                .toList();
        BigDecimal margin = profit.marginAmount(order);
        BigDecimal packingAndDamage = config.getDecimal(ConfigKeys.LOSS_PACKING)
                .add(config.getDecimal(ConfigKeys.LOSS_DAMAGE));

        CourierChoice best = null;
        for (Courier courier : all) {
            double success = pincodeStats.courierSuccessRate(courier.getId(), order.getPincode());
            BigDecimal failShare = BigDecimal.ONE.subtract(BigDecimal.valueOf(success));
            BigDecimal lossIfFails = courier.getCostPerShipment().add(packingAndDamage).add(margin);
            BigDecimal expectedCost = courier.getCostPerShipment().add(failShare.multiply(lossIfFails));
            CourierChoice candidate = new CourierChoice(courier, expectedCost, success);
            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isBetter(CourierChoice candidate, CourierChoice current) {
        int byCost = candidate.expectedCost().compareTo(current.expectedCost());
        if (byCost != 0) {
            return byCost < 0;
        }
        return candidate.successRate() > current.successRate();
    }
}
