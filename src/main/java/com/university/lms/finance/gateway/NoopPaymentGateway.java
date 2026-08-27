package com.university.lms.finance.gateway;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default when {@code lms.payments.provider} names no real gateway. Every call fails loudly —
 * {@code OnlinePaymentService} checks {@link #configured()} before ever reaching this, so reaching
 * {@link #createCheckoutSession} at all would mean that check was bypassed, not that a payment
 * should quietly succeed with nothing behind it.
 */
@Component
@ConditionalOnProperty(name = "lms.payments.provider", havingValue = "none", matchIfMissing = true)
public class NoopPaymentGateway implements PaymentGateway {

    @Override
    public CheckoutSession createCheckoutSession(UUID pendingPaymentId, BigDecimal amount, String currency) {
        throw new IllegalStateException("No payment gateway is configured (lms.payments.provider)");
    }

    @Override
    public Optional<WebhookResult> parseWebhook(String payloadJson, String signatureHeader) {
        return Optional.empty();
    }

    @Override
    public boolean configured() {
        return false;
    }
}
