package com.university.lms.finance.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.university.lms.finance.config.PaymentGatewayProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * E7: Stripe Checkout — a Stripe-hosted page the browser is redirected to, so card data goes
 * straight from the customer to Stripe and never touches this system. Registered only when {@code
 * lms.payments.provider=stripe}; {@link NoopPaymentGateway} is the default otherwise.
 *
 * <p>Every amount here is a {@code price_data} line item built at request time rather than a
 * pre-created Stripe {@code Price} — tuition balances are per-student and per-moment, not a fixed
 * catalog of prices to create and manage in the Stripe dashboard ahead of time.
 */
@Component
@ConditionalOnProperty(name = "lms.payments.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private final StripeClient client;
    private final PaymentGatewayProperties properties;

    public StripePaymentGateway(PaymentGatewayProperties properties) {
        this.properties = properties;
        this.client = new StripeClient(require(properties.stripeSecretKey(), "lms.payments.stripeSecretKey"));
    }

    @Override
    public CheckoutSession createCheckoutSession(UUID pendingPaymentId, BigDecimal amount, String currency) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(pendingPaymentId.toString())
                .setSuccessUrl(appendQueryParam(properties.successUrl(), "session_id={CHECKOUT_SESSION_ID}"))
                .setCancelUrl(properties.cancelUrl())
                .putMetadata("pendingPaymentId", pendingPaymentId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.toLowerCase(java.util.Locale.ROOT))
                                .setUnitAmount(toMinorUnits(amount))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Student account payment")
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = client.checkout().sessions().create(params);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            throw new IllegalStateException("Stripe checkout session creation failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<WebhookResult> parseWebhook(String payloadJson, String signatureHeader) {
        String webhookSecret = properties.stripeWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return Optional.empty();
        }
        Event event;
        try {
            event = client.constructEvent(payloadJson, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException | RuntimeException ex) {
            return Optional.empty();
        }
        if (!"checkout.session.completed".equals(event.getType())
                && !"checkout.session.async_payment_failed".equals(event.getType())) {
            return Optional.empty();
        }
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isEmpty() || !(deserializer.getObject().get() instanceof Session session)) {
            return Optional.empty();
        }
        boolean succeeded = "checkout.session.completed".equals(event.getType())
                && "paid".equals(session.getPaymentStatus());
        return Optional.of(new WebhookResult(
                session.getId(), succeeded, succeeded ? null : "Payment was not completed"));
    }

    @Override
    public boolean configured() {
        return true;
    }

    /** Stripe amounts are in the currency's smallest unit — cents for USD. */
    private static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static String require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when lms.payments.provider=stripe");
        }
        return value;
    }

    /** {@code lms.payments.successUrl} may already carry its own query string (a status flag, say). */
    static String appendQueryParam(String url, String param) {
        return url + (url.contains("?") ? "&" : "?") + param;
    }
}
