package com.university.lms.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * E7: online-payment gateway posture. {@code provider} unset (the default) registers {@link
 * com.university.lms.finance.gateway.NoopPaymentGateway}, which refuses every attempt to
 * initiate a real payment — a deployment that has not supplied its own Stripe account and keys
 * must not silently accept "payments" nobody can actually charge a card for. Setting {@code
 * provider=stripe} and supplying {@code stripeSecretKey}/{@code stripeWebhookSecret} (from the
 * deployer's own Stripe account — this codebase never creates one or holds a real key) registers
 * {@link com.university.lms.finance.gateway.StripePaymentGateway} instead.
 *
 * @param successUrl where Stripe redirects the browser after a completed checkout; {@code
 *     {CHECKOUT_SESSION_ID}} is appended as a query parameter by the gateway itself
 * @param cancelUrl where Stripe redirects the browser if the customer abandons checkout
 */
@ConfigurationProperties("lms.payments")
public record PaymentGatewayProperties(
        @DefaultValue("none") String provider,
        String stripeSecretKey,
        String stripeWebhookSecret,
        @DefaultValue("http://localhost:5173/account/payment-complete") String successUrl,
        @DefaultValue("http://localhost:5173/account") String cancelUrl) {}
