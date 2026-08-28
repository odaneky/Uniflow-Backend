package com.university.lms.finance.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No live Stripe account exists in this environment, so {@link StripePaymentGateway} itself isn't
 * exercised end to end (see {@code OnlinePaymentIntegrationTest}, which runs against the real
 * {@link NoopPaymentGateway} bean instead). This pins the one piece of it that is pure string
 * logic and was actually wrong: {@code lms.payments.successUrl} appending {@code
 * ?session_id={CHECKOUT_SESSION_ID}} unconditionally produced an invalid double-{@code ?} URL for
 * any deployer whose configured successUrl already carried its own query string.
 */
class StripePaymentGatewayTest {

    @Test
    @DisplayName("a successUrl with no existing query string gets a plain ?param")
    void appendsWithQuestionMarkWhenThereIsNoExistingQuery() {
        assertThat(StripePaymentGateway.appendQueryParam("https://app.test/return", "session_id=abc"))
                .isEqualTo("https://app.test/return?session_id=abc");
    }

    @Test
    @DisplayName("a successUrl that already has a query string gets &param, not a second ?")
    void appendsWithAmpersandWhenThereIsAnExistingQuery() {
        assertThat(StripePaymentGateway.appendQueryParam("https://app.test/return?status=success", "session_id=abc"))
                .isEqualTo("https://app.test/return?status=success&session_id=abc");
    }
}
