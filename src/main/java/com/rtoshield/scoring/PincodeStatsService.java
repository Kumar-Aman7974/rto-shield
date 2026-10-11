package com.rtoshield.scoring;

import com.rtoshield.domain.Courier;
import com.rtoshield.domain.CourierPincodeStat;
import com.rtoshield.repo.CourierPincodeStatRepository;
import com.rtoshield.repo.CourierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pincode behaviour comes from the courier_pincode_stats table (attempts and successes per courier per pincode).
 * Person B calls recordResult(...) after every delivery result so the learning loop closes.
 */
@Service
@RequiredArgsConstructor
public class PincodeStatsService {

    private static final double UNKNOWN_SUCCESS_RATE = 0.80;
    private static final double UNKNOWN_REFUSAL_RATE = 0.20;

    private final CourierPincodeStatRepository stats;
    private final CourierRepository couriers;

    /** true if we have at least one recorded delivery attempt in this pincode. */
    public boolean hasHistory(String pincode) {
        return stats.findByPincode(pincode).stream().anyMatch(row -> row.getAttempts() > 0);
    }

    /** Share of deliveries that failed in this pincode, across all couriers (0.0 to 1.0). */
    public double refusalRate(String pincode) {
        List<CourierPincodeStat> rows = stats.findByPincode(pincode);
        int attempts = rows.stream().mapToInt(CourierPincodeStat::getAttempts).sum();
        int successes = rows.stream().mapToInt(CourierPincodeStat::getSuccesses).sum();
        if (attempts == 0) {
            return UNKNOWN_REFUSAL_RATE;
        }
        return (double) (attempts - successes) / attempts;
    }

    /** Success rate (0.0 to 1.0) of the best courier in this pincode. */
    public double bestCourierSuccessRate(String pincode) {
        return stats.findByPincode(pincode).stream()
                .filter(row -> row.getAttempts() > 0)
                .mapToDouble(CourierPincodeStat::successRate)
                .max()
                .orElse(UNKNOWN_SUCCESS_RATE);
    }

    /**
     * Success rate (0.0 to 1.0) of one courier in one pincode.
     * Falls back to the default (0.80) if the pincode is null, there is no row, or the row has no attempts yet.
     */
    public double courierSuccessRate(Long courierId, String pincode) {
        if (pincode == null) {
            return UNKNOWN_SUCCESS_RATE;
        }
        return stats.findByCourierIdAndPincode(courierId, pincode)
                .filter(row -> row.getAttempts() > 0)
                .map(CourierPincodeStat::successRate)
                .orElse(UNKNOWN_SUCCESS_RATE);
    }

    /** Call after a delivery result: success = delivered, not success = failed / RTO. */
    @Transactional
    public void recordResult(Long courierId, String pincode, boolean success) {
        CourierPincodeStat stat = stats.findByCourierIdAndPincode(courierId, pincode).orElseGet(() -> {
            Courier courier = couriers.findById(courierId)
                    .orElseThrow(() -> new IllegalArgumentException("Courier " + courierId + " not found"));
            CourierPincodeStat fresh = new CourierPincodeStat();
            fresh.setCourier(courier);
            fresh.setPincode(pincode);
            return fresh;
        });
        stat.setAttempts(stat.getAttempts() + 1);
        if (success) {
            stat.setSuccesses(stat.getSuccesses() + 1);
        }
        stats.save(stat);
    }
}
