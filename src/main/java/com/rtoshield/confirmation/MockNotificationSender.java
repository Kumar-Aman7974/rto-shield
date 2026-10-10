package com.rtoshield.confirmation;

import com.rtoshield.domain.Notification;
import com.rtoshield.domain.Order;
import com.rtoshield.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** DONE. Saves what the customer "received" so GET /notifications shows it. No real WhatsApp is called. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MockNotificationSender implements NotificationSender {

    private final NotificationRepository notifications;

    @Override
    public void send(Order order, String channel, String message) {
        Notification n = new Notification();
        n.setOrderId(order.getId());
        n.setCustomerId(order.getCustomer().getId());
        n.setChannel(channel);
        n.setMessage(message);
        notifications.save(n);
        log.info("[MOCK {}] to customer {} about order {}: {}", channel, n.getCustomerId(), n.getOrderId(), message);
    }
}
