package com.rtoshield.scoring;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.*;
import com.rtoshield.repo.OrderRepository;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One rule at a time, starting from a perfectly clean order (score 100).
 * Exact reason texts are checked on purpose: they are what the judges read in the demo.
 * No database, no Spring. Run with: mvn clean test
 */
class RuleBasedHealthScorerTest {

    private final Map<String, ConfigEntry> configRows = new HashMap<>();
    private final Fakes.StatStore stats = new Fakes.StatStore();
    private long ordersFromThisPhone = 1;      // 1 = only the order being scored
    private RuleBasedHealthScorer scorer;

    @BeforeEach
    void setUp() {
        configRows.clear();
        stats.rows.clear();
        ordersFromThisPhone = 1;
        stats.setPincode("400001", 95, 95, 95);   // a healthy pincode used by the clean order

        ConfigService config = new ConfigService(Fakes.configRepo(configRows));
        PincodeStatsService pincodeStats = new PincodeStatsService(stats.repo(), Fakes.courierRepo());
        OrderRepository orders = Fakes.proxy(OrderRepository.class, (method, args) -> switch (method) {
            case "countByPhoneAndCreatedAtAfter" -> ordersFromThisPhone;
            default -> throw new UnsupportedOperationException("OrderRepository." + method);
        });
        scorer = new RuleBasedHealthScorer(config, pincodeStats, orders);
    }

    // ------------------------------------------------------------------ the clean order

    @Test
    void cleanOrderIsPerfect() {
        ScoreResult result = check(cleanOrder(), 100, "No risk factors found");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    // ------------------------------------------------------------------ payment and value

    @Test
    void cashOnDeliveryCosts15() {
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.COD);
        check(order, 85, "Cash on delivery (-15)");
    }

    @Test
    void partialCodCosts5() {
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.PARTIAL_COD);
        check(order, 95, "Partial cash on delivery (-5)");
    }

    @Test
    void highValueCodCosts10MoreAtExactly2000() {
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.COD);
        order.setOrderValue(new BigDecimal("2000.00"));
        check(order, 75, "Cash on delivery (-15)", "High-value cash order: Rs 2000 (-10)");
    }

    @Test
    void justBelow2000IsNotHighValue() {
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.COD);
        order.setOrderValue(new BigDecimal("1999.99"));
        check(order, 85, "Cash on delivery (-15)");
    }

    @Test
    void highValuePrepaidOrderIsNotPenalised() {
        Order order = cleanOrder();
        order.setOrderValue(new BigDecimal("5000.00"));
        check(order, 100, "No risk factors found");
    }

    // ------------------------------------------------------------------ address

    @ParameterizedTest
    @CsvSource({"39,75", "40,90", "69,90", "70,100"})
    void addressQualityBands(int quality, int expectedScore) {
        Order order = cleanOrder();
        order.setAddressQuality(quality);
        assertThat(scorer.score(order).score()).isEqualTo(expectedScore);
    }

    @Test
    void poorAddressReasonText() {
        Order order = cleanOrder();
        order.setAddressQuality(39);
        check(order, 75, "Address looks incomplete: quality 39/100 (-25)");
    }

    @Test
    void fairAddressReasonText() {
        Order order = cleanOrder();
        order.setAddressQuality(50);
        check(order, 90, "Address only partly complete: quality 50/100 (-10)");
    }

    // ------------------------------------------------------------------ customer history

    @Test
    void firstTimeCustomerCosts10() {
        Order order = cleanOrder();
        order.getCustomer().setTotalOrders(0);
        check(order, 90, "First-time customer, no delivery history (-10)");
    }

    @Test
    void cautionTierCosts12() {
        Order order = cleanOrder();
        order.getCustomer().setTier(CustomerTier.CAUTION);
        order.getCustomer().setTrustBalance(-20);
        check(order, 88, "Customer has some bad history, trust balance is -20 (-12)");
    }

    @Test
    void prepaidOnlyTierCosts30() {
        Order order = cleanOrder();
        order.getCustomer().setTier(CustomerTier.PREPAID_ONLY);
        order.getCustomer().setTrustBalance(-55);
        check(order, 70, "Customer must prepay, trust balance is -55 (-30)");
    }

    @Test
    void manualReviewTierCosts40() {
        Order order = cleanOrder();
        order.getCustomer().setTier(CustomerTier.MANUAL_REVIEW);
        order.getCustomer().setTrustBalance(-120);
        check(order, 60, "Customer is under manual review, trust balance is -120 (-40)");
    }

    @Test
    void trustedCustomerGetsBonusFrom20Points() {
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.COD);
        order.getCustomer().setTrustBalance(20);
        check(order, 95, "Cash on delivery (-15)", "Trusted customer, trust balance is 20 (+10)");
    }

    @Test
    void nineteenPointsIsNotTrustedYet() {
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.COD);
        order.getCustomer().setTrustBalance(19);
        check(order, 85, "Cash on delivery (-15)");
    }

    // ------------------------------------------------------------------ pincode

    @Test
    void pincodeAtExactly30PercentIsHighRisk() {
        stats.setPincode("110059", 95, 50, 65);     // 210 of 300 delivered, so exactly 30% fail
        Order order = cleanOrder();
        order.setPincode("110059");
        check(order, 80, "High-risk pincode 110059: 30% of deliveries fail (-20)");
    }

    @Test
    void pincodeJustBelow30PercentIsModerate() {
        stats.setPincode("110059", 95, 50, 66);     // 211 of 300 delivered, so 29.7% fail
        Order order = cleanOrder();
        order.setPincode("110059");
        ScoreResult result = scorer.score(order);
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.reasons()).hasSize(1);
        assertThat(result.reasons().get(0)).startsWith("Moderate-risk pincode 110059").endsWith("(-10)");
    }

    @Test
    void pincodeAtExactly15PercentIsModerate() {
        stats.setPincode("110059", 95, 80, 80);     // 255 of 300 delivered, so exactly 15% fail
        Order order = cleanOrder();
        order.setPincode("110059");
        check(order, 90, "Moderate-risk pincode 110059: 15% of deliveries fail (-10)");
    }

    @Test
    void pincodeJustBelow15PercentIsFine() {
        stats.setPincode("110059", 95, 80, 81);     // 256 of 300 delivered, so 14.7% fail
        Order order = cleanOrder();
        order.setPincode("110059");
        check(order, 100, "No risk factors found");
    }

    @Test
    void unknownPincodeCosts8() {
        Order order = cleanOrder();
        order.setPincode("999999");
        check(order, 92, "No delivery history for pincode 999999 (-8)");
    }

    // ------------------------------------------------------------------ courier performance

    @Test
    void weakBestCourierCosts5OnTopOfPincodeRisk() {
        stats.setPincode("110059", 70, 70, 70);     // 30% fail, and the best courier only delivers 70%
        Order order = cleanOrder();
        order.setPincode("110059");
        check(order, 75,
                "High-risk pincode 110059: 30% of deliveries fail (-20)",
                "Even the best courier here delivers only 70% of parcels (-5)");
    }

    @Test
    void bestCourierAtExactly75PercentIsNotWeak() {
        stats.setPincode("110059", 75, 50, 50);     // 41.7% fail, but the best courier delivers exactly 75%
        Order order = cleanOrder();
        order.setPincode("110059");
        check(order, 80, "High-risk pincode 110059: 42% of deliveries fail (-20)");
    }

    // ------------------------------------------------------------------ campaign and margin

    @Test
    void adsCampaignCosts8() {
        Order order = cleanOrder();
        order.setCampaignSource(CampaignSource.ADS);
        check(order, 92, "Order came from an ad campaign (-8)");
    }

    @Test
    void influencerCampaignCosts5() {
        Order order = cleanOrder();
        order.setCampaignSource(CampaignSource.INFLUENCER);
        check(order, 95, "Order came from an influencer campaign (-5)");
    }

    @Test
    void emailCampaignIsFine() {
        Order order = cleanOrder();
        order.setCampaignSource(CampaignSource.EMAIL);
        check(order, 100, "No risk factors found");
    }

    @Test
    void thinMarginBelow25PercentCosts8() {
        Order order = cleanOrder();
        order.setProduct(product("800", "1000"));   // 20.00%
        check(order, 92, "Thin margin 20.00%: a failed delivery wipes out the profit (-8)");
    }

    @Test
    void marginOfExactly25PercentIsFine() {
        Order order = cleanOrder();
        order.setProduct(product("750", "1000"));   // 25.00%
        check(order, 100, "No risk factors found");
    }

    // ------------------------------------------------------------------ odd hour and repeat phone

    @ParameterizedTest
    @CsvSource({"1,59,100", "2,0,95", "3,30,95", "4,59,95", "5,0,100"})
    void oddHourWindowIs0200To0459(int hour, int minute, int expectedScore) {
        Order order = cleanOrder();
        order.setCreatedAt(LocalDateTime.of(2026, 10, 11, hour, minute));
        assertThat(scorer.score(order).score()).isEqualTo(expectedScore);
    }

    @Test
    void oddHourReasonText() {
        Order order = cleanOrder();
        order.setCreatedAt(LocalDateTime.of(2026, 10, 11, 3, 5));
        check(order, 95, "Ordered at an odd hour (03:05) (-5)");
    }

    @Test
    void twoOtherOrdersFromTheSamePhoneCost10() {
        ordersFromThisPhone = 3;                    // this order + 2 others
        check(cleanOrder(), 90, "Same phone placed 2 other orders in 24 hours (-10)");
    }

    @Test
    void oneOtherOrderFromTheSamePhoneIsFine() {
        ordersFromThisPhone = 2;                    // this order + 1 other
        check(cleanOrder(), 100, "No risk factors found");
    }

    // ------------------------------------------------------------------ risk level and clamping

    @ParameterizedTest
    @CsvSource({"30,70,LOW", "31,69,MEDIUM", "60,40,MEDIUM", "61,39,HIGH", "150,0,HIGH"})
    void riskLevelBoundariesAndClamping(int codPenalty, int expectedScore, RiskLevel expectedRisk) {
        Fakes.setConfig(configRows, ConfigKeys.PEN_COD, String.valueOf(codPenalty));
        Order order = cleanOrder();
        order.setPaymentType(PaymentType.COD);

        ScoreResult result = scorer.score(order);

        assertThat(result.score()).isEqualTo(expectedScore);
        assertThat(result.riskLevel()).isEqualTo(expectedRisk);
    }

    @Test
    void changingAPenaltyInConfigChangesTheScoreImmediately() {
        Fakes.setConfig(configRows, ConfigKeys.PEN_ADDRESS_POOR, "40");
        Order order = cleanOrder();
        order.setAddressQuality(10);
        check(order, 60, "Address looks incomplete: quality 10/100 (-40)");
    }

    // ------------------------------------------------------------------ everything at once

    @Test
    void worstCaseOrderFiresEveryRuleAndStopsAtZero() {
        stats.setPincode("110059", 50, 50, 50);
        ordersFromThisPhone = 5;
        Order order = cleanOrder();
        order.setAddressQuality(0);
        order.setPaymentType(PaymentType.COD);
        order.setOrderValue(new BigDecimal("5000.00"));
        order.getCustomer().setTier(CustomerTier.MANUAL_REVIEW);
        order.getCustomer().setTrustBalance(-150);
        order.getCustomer().setTotalOrders(0);
        order.setPincode("110059");
        order.setCampaignSource(CampaignSource.ADS);
        order.setProduct(product("900", "1000"));
        order.setCreatedAt(LocalDateTime.of(2026, 10, 11, 3, 0));

        ScoreResult result = scorer.score(order);

        // 25 + 15 + 10 + 40 + 10 + 20 + 5 + 8 + 8 + 5 + 10 = 156 points of penalty
        assertThat(result.reasons()).hasSize(11);
        assertThat(result.score()).isEqualTo(0);
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    // ------------------------------------------------------------------ the demo story

    @Test
    void demoOrderFirstTimerSmartWatchCodFromAdsInABadPincode() {
        stats.setPincode("110059", 55, 70, 50);     // the same numbers as the seed data
        Order order = cleanOrder();
        order.getCustomer().setTotalOrders(0);
        order.setProduct(product("1800", "2999"));  // 39.98% margin: not thin
        order.setPaymentType(PaymentType.COD);
        order.setOrderValue(new BigDecimal("2999.00"));
        order.setCampaignSource(CampaignSource.ADS);
        order.setPincode("110059");

        ScoreResult result = check(order, 32,
                "Cash on delivery (-15)",
                "High-value cash order: Rs 2999 (-10)",
                "First-time customer, no delivery history (-10)",
                "High-risk pincode 110059: 42% of deliveries fail (-20)",
                "Even the best courier here delivers only 70% of parcels (-5)",
                "Order came from an ad campaign (-8)");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    // ------------------------------------------------------------------ helpers

    private ScoreResult check(Order order, int expectedScore, String... expectedReasons) {
        ScoreResult result = scorer.score(order);
        assertThat(result.score()).isEqualTo(expectedScore);
        assertThat(result.reasons()).containsExactly(expectedReasons);
        return result;
    }

    /** A perfectly healthy order: prepaid, full address, good pincode, repeat customer, 40% margin, noon. */
    private Order cleanOrder() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Clean Customer");
        customer.setPhone("9999999999");
        customer.setTrustBalance(0);
        customer.setTier(CustomerTier.GOOD);
        customer.setTotalOrders(5);

        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(product("600", "1000"));   // 40.00%
        order.setQuantity(1);
        order.setPaymentType(PaymentType.PREPAID);
        order.setCampaignSource(CampaignSource.ORGANIC);
        order.setPhone("9888888888");
        order.setAddressLine("Flat 12, Block C, Dwarka Sector 7");
        order.setCity("New Delhi");
        order.setPincode("400001");
        order.setAddressQuality(100);
        order.setOrderValue(new BigDecimal("1000.00"));
        order.setCreatedAt(LocalDateTime.of(2026, 10, 11, 12, 0));
        return order;
    }

    private static Product product(String cost, String sell) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setCostPrice(new BigDecimal(cost));
        product.setSellingPrice(new BigDecimal(sell));
        return product;
    }
}
