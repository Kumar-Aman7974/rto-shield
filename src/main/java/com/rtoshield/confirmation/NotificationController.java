package com.rtoshield.confirmation;

import com.rtoshield.domain.Notification;
import com.rtoshield.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Shows what the customer "received": GET /notifications?orderId=1 or ?customerId=2. */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notifications;

    @GetMapping("/notifications")
    public List<Notification> list(@RequestParam(required = false) Long orderId,
                                   @RequestParam(required = false) Long customerId) {
        if (orderId != null) {
            return notifications.findByOrderIdOrderByCreatedAtAsc(orderId);
        }
        if (customerId != null) {
            return notifications.findByCustomerIdOrderByCreatedAtAsc(customerId);
        }
        return notifications.findAll();
    }
}
