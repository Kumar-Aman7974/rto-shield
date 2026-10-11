package com.rtoshield.intake;

import com.rtoshield.audit.AuditService;
import com.rtoshield.common.NotFoundException;
import com.rtoshield.confirmation.JourneyExecutor;
import com.rtoshield.domain.*;
import com.rtoshield.intake.dto.CreateOrderRequest;
import com.rtoshield.intake.dto.StoreOrderPayload;
import com.rtoshield.repo.*;
import com.rtoshield.routing.JourneyRouter;
import com.rtoshield.scoring.OrderHealthScorer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * The pipeline for every new order:
 * save -> score (M3) -> route (M4) -> execute the journey (M5) -> audit.
 * The simulator and the demo endpoint call createAndProcess too, so there is only ONE path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final CourierRepository couriers;
    private final AddressQualityService addressQuality;
    private final OrderHealthScorer scorer;
    private final JourneyRouter router;
    private final JourneyExecutor executor;
    private final AuditService audit;

    @Transactional
    public Order createAndProcess(CreateOrderRequest req) {
        Customer customer = customers.findById(req.customerId())
                .orElseThrow(() -> new NotFoundException("Customer " + req.customerId() + " not found"));
        Product product = products.findById(req.productId())
                .orElseThrow(() -> new NotFoundException("Product " + req.productId() + " not found"));

        Order draft = new Order();
        draft.setCustomer(customer);
        draft.setProduct(product);
        draft.setQuantity(req.quantity());
        draft.setPaymentType(req.paymentType());
        draft.setCampaignSource(req.campaignSource() != null ? req.campaignSource() : CampaignSource.ORGANIC);
        draft.setPhone(req.phone());
        draft.setAddressLine(req.addressLine());
        draft.setCity(req.city());
        draft.setPincode(req.pincode());
        draft.setOrderValue(product.getSellingPrice().multiply(BigDecimal.valueOf(req.quantity())));
        draft.setAddressQuality(addressQuality.score(req.addressLine(), req.city(), req.pincode(), req.phone()));
        Order order = orders.save(draft);

        // M3: score
        ScoreResult score = scorer.score(order);
        order.setHealthScore(score.score());
        order.setRiskLevel(score.riskLevel());
        order.setScoreReasons(String.join(" | ", score.reasons()));

        // M4: route
        DecisionOutcome decision = router.decide(order, score);
        order.setJourney(decision.journey());
        order.setDecisionReason(decision.reason());
        order.setExpectedLoss(decision.expectedLoss());
        order.setActionCost(decision.actionCost());
        if (decision.courierId() != null) {
            couriers.findById(decision.courierId()).ifPresent(order::setCourier);
        }

        // M5: carry out the journey (sends the message, sets the status)
        executor.execute(order, decision);

        audit.record(order.getId(), "JOURNEY_" + decision.journey(), decision.reason(),
                "score=" + score.score()
                        + ", risk=" + score.riskLevel()
                        + ", trust=" + customer.getTrustBalance()
                        + ", tier=" + customer.getTier()
                        + ", expectedLoss=" + decision.expectedLoss()
                        + ", actionCost=" + decision.actionCost());

        // counted AFTER scoring, so the scorer still sees a first-time customer as first-time
        customer.setTotalOrders(customer.getTotalOrders() + 1);
        return order;
    }

    /** Store webhook: translate the Shopify-style payload into our own request, then use the same pipeline. */
    @Transactional
    public Order createFromStore(StoreOrderPayload payload) {
        StoreOrderPayload.StoreCustomer sc = payload.customer();
        Customer customer = customers.findByPhone(sc.phone()).orElseGet(() -> {
            Customer fresh = new Customer();
            fresh.setName(sc.name());
            fresh.setPhone(sc.phone());
            fresh.setEmail(sc.email());
            return customers.save(fresh);
        });

        StoreOrderPayload.StoreLineItem item = payload.lineItems().get(0);
        boolean cod = payload.paymentGatewayNames() != null && payload.paymentGatewayNames().stream()
                .map(n -> n.toLowerCase(Locale.ROOT))
                .anyMatch(n -> n.contains("cod") || n.contains("cash"));

        CreateOrderRequest req = new CreateOrderRequest(
                customer.getId(),
                item.productId(),
                item.quantity(),
                cod ? PaymentType.COD : PaymentType.PREPAID,
                mapSource(payload.sourceName()),
                payload.shippingAddress().address1(),
                payload.shippingAddress().city(),
                payload.shippingAddress().zip(),
                sc.phone());
        return createAndProcess(req);
    }

    @Transactional(readOnly = true)
    public Order get(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Order> list() {
        return orders.findAll();
    }

    private CampaignSource mapSource(String source) {
        if (source == null) {
            return CampaignSource.ORGANIC;
        }
        return switch (source.toLowerCase(Locale.ROOT)) {
            case "ads" -> CampaignSource.ADS;
            case "influencer" -> CampaignSource.INFLUENCER;
            case "email" -> CampaignSource.EMAIL;
            default -> CampaignSource.ORGANIC;
        };
    }
}
