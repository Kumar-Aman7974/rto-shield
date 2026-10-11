package com.rtoshield.routing;

import com.rtoshield.config.ConfigKeys;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One scenario per rule of the router. The exact reason texts are checked on purpose: judges read them.
 * Every one of the 7 journeys is reached by at least one test.
 * Expected numbers were calculated separately (not by the Java code) from the seed data.
 * No database, no Spring.
 */
class JourneyRouterTest {

    private static final Product CASE = product("Phone Case", "40", "199");            // margin 159
    private static final Product BUDS = product("Wireless Earbuds", "900", "1199");    // margin 299
    private static final Product WATCH = product("Smart Watch", "1800", "2999");       // margin 1199

    private final Map<String, ConfigEntry> configRows = new HashMap<>();
    private final Fakes.StatStore stats = new Fakes.StatStore();
    private JourneyRouter router;

    @BeforeEach
    void setUp() {
        configRows.clear();
        stats.rows.clear();
        stats.setPincode("400001", 92, 94, 90);
        stats.setPincode("110059", 55, 70, 50);

        List<Courier> couriers = List.of(courier(1L, "FastShip", "60"), courier(2L, "SafeExpress", "75"), courier(3L, "QuickPost", "50"));
        ConfigService config = new ConfigService(Fakes.configRepo(configRows));
        PincodeStatsService pincodeStats = new PincodeStatsService(stats.repo(), Fakes.courierRepo(couriers));
        ProfitCalculator profit = new ProfitCalculator(config);
        CourierRecommender recommender = new CourierRecommender(Fakes.courierRepo(couriers), pincodeStats, profit, config);
        router = new JourneyRouter(config, profit, recommender);
    }

    @Test
    void manualReviewCodGoesToHumanReview() {
        DecisionOutcome outcome = router.decide(
                order(WATCH, PaymentType.COD, CustomerTier.MANUAL_REVIEW, -120, 6, 100, "400001"), score(RiskLevel.HIGH));

        assertOutcome(outcome, Journey.HUMAN_REVIEW, null, "90.00", "30.00",
                "Customer is under manual review (trust balance -120): a person must approve before shipping.");
    }

    @Test
    void manualReviewButPrepaidShipsDirectly() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.PREPAID, CustomerTier.MANUAL_REVIEW, -120, 6, 100, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.PREPAID_SHIP, 3L, "10.00", "0.00",
                "Prepaid order: no cash-on-delivery risk, ship directly. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void poorAddressCodGoesToAddressVerify() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 39, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.ADDRESS_VERIFY, null, "10.00", "1.00",
                "Address quality is only 39/100: confirm the address with the customer before shipping.");
    }

    @Test
    void poorAddressPrepaidAlsoGoesToAddressVerify() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.PREPAID, CustomerTier.GOOD, 0, 5, 39, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.ADDRESS_VERIFY, null, "10.00", "1.00",
                "Address quality is only 39/100: confirm the address with the customer before shipping.");
    }

    @Test
    void addressQualityOf40IsNotVerified() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 40, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.FULL_COD, 3L, "10.00", "0.00",
                "Best expected profit: FULL_COD Rs 141.05 (next best: OTP_CONFIRM Rs 137.50). Risk LOW: 5% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void manualReviewBeatsPoorAddress() {
        DecisionOutcome outcome = router.decide(
                order(WATCH, PaymentType.COD, CustomerTier.MANUAL_REVIEW, -120, 6, 10, "400001"), score(RiskLevel.HIGH));

        assertOutcome(outcome, Journey.HUMAN_REVIEW, null, "90.00", "30.00",
                "Customer is under manual review (trust balance -120): a person must approve before shipping.");
    }

    @Test
    void prepaidOrderShipsDirectly() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.PREPAID, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.PREPAID_SHIP, 3L, "10.00", "0.00",
                "Prepaid order: no cash-on-delivery risk, ship directly. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void partialCodOrderGoesToPartialCod() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.PARTIAL_COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.PARTIAL_COD, 3L, "10.00", "1.00",
                "Customer chose partial cash on delivery: collect the advance, then ship. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void prepaidOnlyCustomerWithCodMustPrepay() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.PREPAID_ONLY, -55, 5, 100, "400001"), score(RiskLevel.MEDIUM));

        assertOutcome(outcome, Journey.PREPAID_INCENTIVE, 3L, "40.00", "6.97",
                "Customer must prepay (trust balance -55): ask for online payment with a small discount. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void lowRiskCheapOrderShipsWithoutFriction() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.FULL_COD, 3L, "10.00", "0.00",
                "Best expected profit: FULL_COD Rs 141.05 (next best: OTP_CONFIRM Rs 137.50). Risk LOW: 5% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void mediumRiskCheapOrderAsksForOtp() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.MEDIUM));

        assertOutcome(outcome, Journey.OTP_CONFIRM, 3L, "40.00", "1.00",
                "Best expected profit: OTP_CONFIRM Rs 99.84 (next best: PREPAID_INCENTIVE Rs 97.15). Risk MEDIUM: 20% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void highRiskCheapOrderAsksForPrepayment() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.HIGH));

        assertOutcome(outcome, Journey.PREPAID_INCENTIVE, 3L, "90.00", "6.97",
                "Best expected profit: PREPAID_INCENTIVE Rs 58.36 (next best: PARTIAL_COD Rs 44.46). Risk HIGH: 45% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void highRiskExpensiveOrderOnlyAsksForOtp() {
        DecisionOutcome outcome = router.decide(
                order(WATCH, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "110059"), score(RiskLevel.HIGH));

        assertOutcome(outcome, Journey.OTP_CONFIRM, 2L, "90.00", "1.00",
                "Best expected profit: OTP_CONFIRM Rs 580.48 (next best: FULL_COD Rs 569.45). Risk HIGH: 45% chance of failure, margin Rs 1199.00, loss if returned Rs 200.00. Courier: SafeExpress (expected cost Rs 472.20).");
    }

    @Test
    void highRiskThinMarginOrderUsesPartialCod() {
        DecisionOutcome outcome = router.decide(
                order(BUDS, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.HIGH));

        assertOutcome(outcome, Journey.PARTIAL_COD, 3L, "90.00", "1.00",
                "Best expected profit: PARTIAL_COD Rs 112.22 (next best: OTP_CONFIRM Rs 110.23). Risk HIGH: 45% chance of failure, margin Rs 299.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 89.90).");
    }

    @Test
    void mediumRiskHighMarginOrderStillShipsDirectly() {
        DecisionOutcome outcome = router.decide(
                order(WATCH, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.MEDIUM));

        assertOutcome(outcome, Journey.FULL_COD, 2L, "40.00", "0.00",
                "Best expected profit: FULL_COD Rs 919.20 (next best: OTP_CONFIRM Rs 890.24). Risk MEDIUM: 20% chance of failure, margin Rs 1199.00, loss if returned Rs 200.00. Courier: SafeExpress (expected cost Rs 154.44).");
    }

    @Test
    void firstTimerCannotShipWithoutConfirmation() {
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 0, 100, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.OTP_CONFIRM, 3L, "10.00", "1.00",
                "First-time customer, so shipping without confirmation is not allowed. Best expected profit: OTP_CONFIRM Rs 137.50 (next best: PARTIAL_COD Rs 128.42). Risk LOW: 5% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void tieGoesToTheLowerFrictionJourney() {
        Fakes.setConfig(configRows, ConfigKeys.JOURNEY_OTP_FAIL_REDUCTION, "0");
        Fakes.setConfig(configRows, ConfigKeys.JOURNEY_OTP_DROPOFF, "0");
        Fakes.setConfig(configRows, ConfigKeys.COST_WHATSAPP, "0");
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.LOW));

        assertOutcome(outcome, Journey.FULL_COD, 3L, "10.00", "0.00",
                "Best expected profit: FULL_COD Rs 141.05 (next best: OTP_CONFIRM Rs 141.05). Risk LOW: 5% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    @Test
    void changingTheDropoffInConfigChangesTheChoice() {
        Fakes.setConfig(configRows, ConfigKeys.DROPOFF_PREPAID_RATE, "0.60");
        DecisionOutcome outcome = router.decide(
                order(CASE, PaymentType.COD, CustomerTier.GOOD, 0, 5, 100, "400001"), score(RiskLevel.HIGH));

        assertOutcome(outcome, Journey.PARTIAL_COD, 3L, "90.00", "1.00",
                "Best expected profit: PARTIAL_COD Rs 44.46 (next best: OTP_CONFIRM Rs 37.08). Risk HIGH: 45% chance of failure, margin Rs 159.00, loss if returned Rs 200.00. Courier: QuickPost (expected cost Rs 75.90).");
    }

    // ------------------------------------------------------------------ helpers

    private static void assertOutcome(DecisionOutcome outcome, Journey journey, Long courierId,
                                      String expectedLoss, String actionCost, String reason) {
        assertThat(outcome.journey()).isEqualTo(journey);
        assertThat(outcome.courierId()).isEqualTo(courierId);
        assertThat(outcome.expectedLoss()).isEqualByComparingTo(expectedLoss);
        assertThat(outcome.actionCost()).isEqualByComparingTo(actionCost);
        assertThat(outcome.reason()).isEqualTo(reason);
    }

    private static ScoreResult score(RiskLevel risk) {
        return new ScoreResult(50, risk, List.of());
    }

    private static Order order(Product product, PaymentType payment, CustomerTier tier, int trustBalance,
                               int totalOrders, int addressQuality, String pincode) {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Test Customer");
        customer.setPhone("9999999999");
        customer.setTier(tier);
        customer.setTrustBalance(trustBalance);
        customer.setTotalOrders(totalOrders);

        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(product);
        order.setQuantity(1);
        order.setPaymentType(payment);
        order.setAddressQuality(addressQuality);
        order.setPincode(pincode);
        order.setOrderValue(product.getSellingPrice());
        return order;
    }

    private static Courier courier(Long id, String name, String cost) {
        Courier courier = new Courier();
        courier.setId(id);
        courier.setName(name);
        courier.setCostPerShipment(new BigDecimal(cost));
        return courier;
    }

    private static Product product(String name, String cost, String sell) {
        Product product = new Product();
        product.setName(name);
        product.setCostPrice(new BigDecimal(cost));
        product.setSellingPrice(new BigDecimal(sell));
        return product;
    }
}
