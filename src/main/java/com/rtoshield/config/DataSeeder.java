package com.rtoshield.config;

import com.rtoshield.domain.*;
import com.rtoshield.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Loads starting data on every start-up (each block only runs if its table is empty).
 * Seed customers: id 2 and 8 are first-timers; 4, 5, 7 have bad history.
 * To reset everything: docker-compose down -v, then start again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ConfigEntryRepository configRepo;
    private final CourierRepository courierRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final CourierPincodeStatRepository statRepo;

    @Override
    public void run(String... args) {
        seedConfig();
        seedCouriers();
        seedProducts();
        seedCustomers();
        seedPincodeStats();
        log.info("Seed data ready: {} customers, {} products, {} couriers",
                customerRepo.count(), productRepo.count(), courierRepo.count());
    }

    private void seedConfig() {
        ConfigKeys.DEFAULTS.forEach((key, value) -> {
            if (!configRepo.existsById(key)) {
                configRepo.save(new ConfigEntry(key, value, "default"));
            }
        });
    }

    private void seedCouriers() {
        if (courierRepo.count() > 0) {
            return;
        }
        courier("FastShip", "60");
        courier("SafeExpress", "75");
        courier("QuickPost", "50");
    }

    private void courier(String name, String cost) {
        Courier c = new Courier();
        c.setName(name);
        c.setCostPerShipment(new BigDecimal(cost));
        courierRepo.save(c);
    }

    private void seedProducts() {
        if (productRepo.count() > 0) {
            return;
        }
        product("Cotton Kurta", "Fashion", "400", "899");
        product("Wireless Earbuds", "Electronics", "900", "1199");   // thin margin
        product("Phone Case", "Accessories", "40", "199");           // the Rs 200 order
        product("Smart Watch", "Electronics", "1800", "2999");       // the Rs 3,000 order
        product("Face Serum", "Beauty", "150", "599");
    }

    private void product(String name, String category, String cost, String sell) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setCostPrice(new BigDecimal(cost));
        p.setSellingPrice(new BigDecimal(sell));
        productRepo.save(p);
    }

    private void seedCustomers() {
        if (customerRepo.count() > 0) {
            return;
        }
        customer("Riya Sharma", "9876500001", 30, CustomerTier.GOOD, 8);
        customer("Amit Verma", "9876500002", 0, CustomerTier.GOOD, 0);          // first-timer
        customer("Sana Khan", "9876500003", -20, CustomerTier.CAUTION, 4);
        customer("Rahul Das", "9876500004", -55, CustomerTier.PREPAID_ONLY, 5);
        customer("Vikram Singh", "9876500005", -120, CustomerTier.MANUAL_REVIEW, 6);
        customer("Neha Gupta", "9876500006", 10, CustomerTier.GOOD, 3);
        customer("Karan Mehta", "9876500007", -45, CustomerTier.PREPAID_ONLY, 4);
        customer("Pooja Yadav", "9876500008", 0, CustomerTier.GOOD, 0);         // first-timer
    }

    private void customer(String name, String phone, int trust, CustomerTier tier, int orders) {
        Customer c = new Customer();
        c.setName(name);
        c.setPhone(phone);
        c.setTrustBalance(trust);
        c.setTier(tier);
        c.setTotalOrders(orders);
        customerRepo.save(c);
    }

    /** Out of 100 attempts per courier per pincode. Columns: FastShip, SafeExpress, QuickPost. */
    private void seedPincodeStats() {
        if (statRepo.count() > 0) {
            return;
        }
        List<Courier> couriers = courierRepo.findAll(Sort.by("id"));
        if (couriers.size() != 3) {
            return;
        }
        String[] pincodes = {"110059", "800014", "400001", "560001", "700001", "226010"};
        int[][] successes = {
                {55, 70, 50},   // 110059: bad area
                {78, 85, 72},   // 800014
                {92, 94, 90},   // 400001: good area
                {90, 93, 88},   // 560001: good area
                {80, 86, 75},   // 700001
                {60, 72, 58}    // 226010: bad area
        };
        for (int i = 0; i < pincodes.length; i++) {
            for (int c = 0; c < 3; c++) {
                CourierPincodeStat stat = new CourierPincodeStat();
                stat.setCourier(couriers.get(c));
                stat.setPincode(pincodes[i]);
                stat.setAttempts(100);
                stat.setSuccesses(successes[i][c]);
                statRepo.save(stat);
            }
        }
    }
}
