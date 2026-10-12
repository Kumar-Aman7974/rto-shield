package com.rtoshield.delivery;

import com.rtoshield.domain.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Loads an order and locks its row until the current transaction ends (SELECT ... FOR UPDATE).
 * Two courier calls for the same order (for example a webhook retry that arrives at the same moment)
 * therefore run one after the other, so the duplicate check in DeliveryStateService always sees the first call's event.
 * It lives in the delivery folder so Person A's OrderRepository does not have to change.
 * Must be called inside a transaction.
 */
@Component
public class OrderLocker {

    private final EntityManager em;

    public OrderLocker(EntityManager em) {
        this.em = em;
    }

    public Optional<Order> lock(Long orderId) {
        List<Order> rows = em.createQuery("select o from Order o where o.id = :id", Order.class)
                .setParameter("id", orderId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return rows.stream().findFirst();
    }
}
