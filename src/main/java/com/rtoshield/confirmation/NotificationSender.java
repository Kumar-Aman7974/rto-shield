package com.rtoshield.confirmation;

import com.rtoshield.domain.Order;

/** Contract (M5). Today the only implementation is the mock; a real Twilio sender could replace it later. */
public interface NotificationSender {
    void send(Order order, String channel, String message);
}
