package com.university.lms.finance.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.finance.service.OnlinePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E7: where the payment provider tells us what actually happened. No bearer token — Stripe cannot
 * hold one of ours — so this is publicly reachable and its own signature check ({@code
 * PaymentGateway.parseWebhook}) is the only authentication it has. Always {@code 200}: a payment
 * provider retries a non-2xx response, and a webhook whose response reveals whether its signature
 * or reference was valid is a signature oracle handed to whoever finds this URL.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class PaymentWebhookController {

    private final OnlinePaymentService onlinePaymentService;

    public PaymentWebhookController(OnlinePaymentService onlinePaymentService) {
        this.onlinePaymentService = onlinePaymentService;
    }

    @AccessClass(PUBLIC)
    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(
            @RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        onlinePaymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
