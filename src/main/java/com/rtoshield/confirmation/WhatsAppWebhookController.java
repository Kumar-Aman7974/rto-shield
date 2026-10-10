package com.rtoshield.confirmation;

import com.rtoshield.domain.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Postman plays the customer here: reply "YES", "NO", or the OTP. */
@RestController
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final JourneyExecutor executor;

    public record CustomerReply(@NotNull Long orderId, @NotBlank String text) {
    }

    @PostMapping("/webhooks/whatsapp/reply")
    public Map<String, Object> reply(@Valid @RequestBody CustomerReply reply) {
        OrderStatus status = executor.handleReply(reply.orderId(), reply.text());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", reply.orderId());
        out.put("statusAfterReply", status);
        return out;
    }
}
