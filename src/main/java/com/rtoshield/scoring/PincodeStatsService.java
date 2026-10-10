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
 * DONE (basic version). Pincode behaviour comes from courier_pincode_stats.
 * Person B calls recordResult(...) after every delivery result so the learning loop closes.
 */
@Service
@RequiredArgsConstructor
public class PincodeStatsService {

    private static final double UNKNOWN_PINCODE_SUCCESS_RATE = 0.80;

    private final CourierPincodeStatRepository stats;
    private final CourierRepository couriers;

    /** Share of deliveries that failed in this pincode, across all couriers (0.0 to 1.0). */
    public double refusalRate(String pincode) {
        List<CourierPincodeStat> rows = stats.findByPincode(pincode);
        int attempts = rows.stream().mapToInt(CourierPincodeStat::getAttempts).sum();
        int successes = rows.stream().mapToInt(CourierPincodeStat::getSuccesses).sum();
        if (attempts == 0) {
            return 1.0 - UNKNOWN_PINCODE_SUCCESS_RATE;
        }
        return 1.0 - ((double) successes / attempts);
    }

    /** Success rate (0.0 to 1.0) of one courier in one pincode. */
    public double courierSuccessRate(Long courierId, String pincode) {
        return stats.findByCourierIdAndPincode(courierId, pincode)
                .map(CourierPincodeStat::successRate)
                .orElse(UNKNOWN_PINCODE_SUCCESS_RATE);
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
