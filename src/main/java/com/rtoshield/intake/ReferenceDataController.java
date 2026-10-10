package com.rtoshield.intake;

import com.rtoshield.domain.Courier;
import com.rtoshield.domain.CourierPincodeStat;
import com.rtoshield.domain.Customer;
import com.rtoshield.domain.Product;
import com.rtoshield.repo.CourierPincodeStatRepository;
import com.rtoshield.repo.CourierRepository;
import com.rtoshield.repo.CustomerRepository;
import com.rtoshield.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only lists of the seeded data (M1 "done when": GET returns seeded data). */
@RestController
@RequiredArgsConstructor
public class ReferenceDataController {

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final CourierRepository couriers;
    private final CourierPincodeStatRepository stats;

    @GetMapping("/customers")
    public List<Customer> customers() {
        return customers.findAll();
    }

    @GetMapping("/products")
    public List<Product> products() {
        return products.findAll();
    }

    @GetMapping("/couriers")
    public List<Courier> couriers() {
        return couriers.findAll();
    }

    @GetMapping("/courier-stats")
    public List<CourierPincodeStat> courierStats() {
        return stats.findAll();
    }
}
