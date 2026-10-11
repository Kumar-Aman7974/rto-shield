package com.rtoshield.routing;

import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.*;
import com.rtoshield.scoring.PincodeStatsService;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Courier choice by expected cost: shipping + (1 - success rate) x (return shipping + packing + damage + lost margin).
 * Numbers are the seed data: FastShip 60, SafeExpress 75, QuickPost 50.
 */
class CourierRecommenderTest {

    private static final Product CASE = product("40", "199");      // margin 159
    private static final Product WATCH = product("1800", "2999");  // margin 1199

    private final Map<String, ConfigEntry> configRows = new HashMap<>();
    private final Fakes.StatStore stats = new Fakes.StatStore();

    @BeforeEach
    void setUp() {
        configRows.clear();
        stats.rows.clear();
        stats.setPincode("400001", 92, 94, 90);
        stats.setPincode("110059", 55, 70, 50);
    }

    @Test
    void cheapOrderInAGoodPincodePicksTheCheapestCourier() {
        CourierRecommender.CourierChoice choice = recommender().recommend(order(CASE, "400001")).orElseThrow();

        assertThat(choice.courier().getId()).isEqualTo(3L);
        assertThat(choice.courier().getName()).isEqualTo("QuickPost");
        assertThat(choice.expectedCost()).isEqualByComparingTo("75.9");
        assertThat(choice.successRate()).isEqualTo(0.9);
    }

    @Test
    void valuableOrderInAWeakPincodePicksTheReliableCourier() {
        CourierRecommender.CourierChoice choice = recommender().recommend(order(WATCH, "110059")).orElseThrow();

        assertThat(choice.courier().getId()).isEqualTo(2L);
        assertThat(choice.courier().getName()).isEqualTo("SafeExpress");
        assertThat(choice.expectedCost()).isEqualByComparingTo("472.2");
        assertThat(choice.successRate()).isEqualTo(0.7);
    }

    @Test
    void unknownPincodeFallsBackToTheDefaultRate() {
        CourierRecommender.CourierChoice choice = recommender().recommend(order(CASE, "999999")).orElseThrow();

        assertThat(choice.courier().getId()).isEqualTo(3L);
        assertThat(choice.courier().getName()).isEqualTo("QuickPost");
        assertThat(choice.expectedCost()).isEqualByComparingTo("101.8");
        assertThat(choice.successRate()).isEqualTo(0.8);
    }

    @Test
    void nullPincodeUsesTheDefaultRateAndDoesNotCrash() {
        CourierRecommender.CourierChoice choice = recommender().recommend(order(CASE, null)).orElseThrow();

        assertThat(choice.courier().getName()).isEqualTo("QuickPost");
        assertThat(choice.expectedCost()).isEqualByComparingTo("101.8");
    }

    @Test
    void equalCouriersTieToTheLowerId() {
        List<Courier> twins = List.of(courier(2L, "Twin B", "50"), courier(1L, "Twin A", "50"));

        CourierRecommender.CourierChoice choice = recommender(twins).recommend(order(CASE, "999999")).orElseThrow();

        assertThat(choice.courier().getId()).isEqualTo(1L);
    }

    @Test
    void noCouriersMeansNoRecommendation() {
        Optional<CourierRecommender.CourierChoice> choice = recommender(List.of()).recommend(order(CASE, "400001"));

        assertThat(choice).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private CourierRecommender recommender() {
        return recommender(List.of(courier(1L, "FastShip", "60"), courier(2L, "SafeExpress", "75"), courier(3L, "QuickPost", "50")));
    }

    private CourierRecommender recommender(List<Courier> couriers) {
        ConfigService config = new ConfigService(Fakes.configRepo(configRows));
        PincodeStatsService pincodeStats = new PincodeStatsService(stats.repo(), Fakes.courierRepo(couriers));
        return new CourierRecommender(Fakes.courierRepo(couriers), pincodeStats, new ProfitCalculator(config), config);
    }

    private static Courier courier(Long id, String name, String cost) {
        Courier courier = new Courier();
        courier.setId(id);
        courier.setName(name);
        courier.setCostPerShipment(new BigDecimal(cost));
        return courier;
    }

    private static Order order(Product product, String pincode) {
        Order order = new Order();
        order.setProduct(product);
        order.setQuantity(1);
        order.setPincode(pincode);
        order.setOrderValue(product.getSellingPrice());
        return order;
    }

    private static Product product(String cost, String sell) {
        Product product = new Product();
        product.setCostPrice(new BigDecimal(cost));
        product.setSellingPrice(new BigDecimal(sell));
        return product;
    }
}
