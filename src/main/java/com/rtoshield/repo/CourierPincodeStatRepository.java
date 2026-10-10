package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourierPincodeStatRepository extends JpaRepository<CourierPincodeStat, Long> {
    Optional<CourierPincodeStat> findByCourierIdAndPincode(Long courierId, String pincode);

    List<CourierPincodeStat> findByPincode(String pincode);
}
