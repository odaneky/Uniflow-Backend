package com.university.lms.finance.gateway;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * E7: where an online payment actually happens. Internal to the {@code finance} module — {@code
 * OnlinePaymentService} is the only caller, the same way {@code document.storage.BlobStore} is
 * internal to {@code document} and only {@code DocumentService} calls it.
 *
 * <p>Hosted checkout only: this interface has no method that accepts a card number, an expiry
 * date, or a CVC, and never will — the whole point is that card data goes straight from the
 * customer's browser to the provider and never touches this system at all.
 */
public interface PaymentGateway {

    /** @param redirectUrl where the browser sends the student to actually pay */
    record CheckoutSession(String providerReference, String redirectUrl) {}

    /** A provider event, once its signature has checked out — never trust a webhook body unverified. */
    record WebhookResult(String providerReference, boolean succeeded, String failureReason) {}

    /**
     * Starts a hosted checkout for this amount. {@code pendingPaymentId} travels as the session's
     * own reference (Stripe: {@code client_reference_id}) so the eventual webhook can be tied back
     * to it even before {@link CheckoutSession#providerReference} is persisted.
     */
    CheckoutSession createCheckoutSession(UUID pendingPaymentId, BigDecimal amount, String currency);

    /**
     * Verifies {@code signatureHeader} against {@code payloadJson} and parses the event if it
     * checks out. Empty — never an exception — when the signature is missing, malformed, or does
     * not match: a forged or replayed webhook is indistinguishable from "nothing happened" as far
     * as the caller is concerned.
     */
    Optional<WebhookResult> parseWebhook(String payloadJson, String signatureHeader);

    /** Whether this instance can actually reach a provider — false for {@link NoopPaymentGateway}. */
    boolean configured();
}
