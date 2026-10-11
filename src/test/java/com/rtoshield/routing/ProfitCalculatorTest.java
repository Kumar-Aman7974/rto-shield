package com.rtoshield.routing;

import com.rtoshield.config.ConfigKeys;
import com.rtoshield.config.ConfigService;
import com.rtoshield.domain.*;
import com.rtoshield.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The money maths, checked against hand-calculated numbers. No database, no Spring. */
class ProfitCalculatorTest {

    private static final Product CASE = product("Phone Case", "40", "199");       // margin 159
    private static final Product WATCH = product("Smart Watch", "1800", "2999");  // margin 1199

    private final Map<String, ConfigEntry> configRows = new HashMap<>();
    private ProfitCalculator calc;

    @BeforeEach
    void setUp() {
        configRows.clear();
        calc = new ProfitCalculator(new ConfigService(Fakes.configRepo(configRows)));
    }

    @Test
    void returnLossIsShippingPackingAndDamage() {
        assertThat(calc.returnLoss()).isEqualByComparingTo("200");     // 150 + 20 + 30
    }

    @Test
    void failureChancesComeFromConfig() {
        assertThat(calc.pFailDecimal(RiskLevel.LOW)).isEqualByComparingTo("0.05");
        assertThat(calc.pFailDecimal(RiskLevel.MEDIUM)).isEqualByComparingTo("0.20");
        assertThat(calc.pFailDecimal(RiskLevel.HIGH)).isEqualByComparingTo("0.45");
        assertThat(calc.pFail(RiskLevel.HIGH)).isEqualTo(0.45);
    }

    @Test
    void marginIsSellingMinusCostTimesQuantity() {
        assertThat(calc.marginAmount(order(WATCH, 1))).isEqualByComparingTo("1199");
        assertThat(calc.marginAmount(order(WATCH, 2))).isEqualByComparingTo("2398");
    }

    @Test
    void baselineExpectedLossIsChanceTimesReturnLoss() {
        assertThat(calc.baselineExpectedLoss(order(CASE, 1), RiskLevel.LOW)).isEqualByComparingTo("10.00");
        assertThat(calc.baselineExpectedLoss(order(CASE, 1), RiskLevel.MEDIUM)).isEqualByComparingTo("40.00");
        assertThat(calc.baselineExpectedLoss(order(CASE, 1), RiskLevel.HIGH)).isEqualByComparingTo("90.00");
    }

    @Test
    void changingAConfigNumberChangesTheMaths() {
        Fakes.setConfig(configRows, ConfigKeys.LOSS_PACKING, "120");
        assertThat(calc.returnLoss()).isEqualByComparingTo("300");
    }

    @Test
    void everyJourneyHasItsCost() {
        Order order = order(CASE, 1);
        assertThat(calc.journeyCost(Journey.FULL_COD, order)).isEqualByComparingTo("0");
        assertThat(calc.journeyCost(Journey.PREPAID_SHIP, order)).isEqualByComparingTo("0");
        assertThat(calc.journeyCost(Journey.OTP_CONFIRM, order)).isEqualByComparingTo("1");
        assertThat(calc.journeyCost(Journey.PARTIAL_COD, order)).isEqualByComparingTo("1");
        assertThat(calc.journeyCost(Journey.ADDRESS_VERIFY, order)).isEqualByComparingTo("1");
        assertThat(calc.journeyCost(Journey.PREPAID_INCENTIVE, order)).isEqualByComparingTo("6.97");   // 1 + 3% of 199
        assertThat(calc.journeyCost(Journey.HUMAN_REVIEW, order)).isEqualByComparingTo("30");
    }

    @Test
    void expectedProfitOfASmartWatchAtHighRisk() {
        Order order = order(WATCH, 1);
        assertProfit(Journey.FULL_COD, order, RiskLevel.HIGH, "569.45", "0");
        assertProfit(Journey.OTP_CONFIRM, order, RiskLevel.HIGH, "580.4775", "1");
        assertProfit(Journey.PARTIAL_COD, order, RiskLevel.HIGH, "547.8160", "1");
        assertProfit(Journey.PREPAID_INCENTIVE, order, RiskLevel.HIGH, "460.5625", "90.97");
        assertProfit(Journey.HUMAN_REVIEW, order, RiskLevel.HIGH, "545.5050", "30");
    }

    @Test
    void expectedProfitOfAPhoneCaseAtLowRisk() {
        Order order = order(CASE, 1);
        assertProfit(Journey.FULL_COD, order, RiskLevel.LOW, "141.05", "0");
        assertProfit(Journey.OTP_CONFIRM, order, RiskLevel.LOW, "137.4975", "1");
    }

    @Test
    void journeysWithoutAProfitModelAreRejected() {
        Order order = order(CASE, 1);
        assertThatThrownBy(() -> calc.evaluate(Journey.ADDRESS_VERIFY, order, RiskLevel.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc.evaluate(Journey.PREPAID_SHIP, order, RiskLevel.LOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertProfit(Journey journey, Order order, RiskLevel risk, String profit, String cost) {
        ProfitCalculator.JourneyOption option = calc.evaluate(journey, order, risk);
        assertThat(option.journey()).isEqualTo(journey);
        assertThat(option.expectedProfit()).isEqualByComparingTo(profit);
        assertThat(option.actionCost()).isEqualByComparingTo(cost);
    }

    private static Order order(Product product, int quantity) {
        Order order = new Order();
        order.setProduct(product);
        order.setQuantity(quantity);
        order.setOrderValue(product.getSellingPrice().multiply(BigDecimal.valueOf(quantity)));
        return order;
    }

    private static Product product(String name, String cost, String sell) {
        Product product = new Product();
        product.setName(name);
        product.setCostPrice(new BigDecimal(cost));
        product.setSellingPrice(new BigDecimal(sell));
        return product;
    }
}
