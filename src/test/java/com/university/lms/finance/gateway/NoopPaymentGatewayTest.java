package com.university.lms.finance.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The default when no real gateway is configured — every attempt to actually pay must fail loudly. */
class NoopPaymentGatewayTest {

    private final NoopPaymentGateway gateway = new NoopPaymentGateway();

    @Test
    void reportsItselfAsNotConfigured() {
        assertThat(gateway.configured()).isFalse();
    }

    @Test
    void refusesToCreateACheckoutSession() {
        assertThatThrownBy(() -> gateway.createCheckoutSession(UUID.randomUUID(), new BigDecimal("10.00"), "USD"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parsesNoWebhookEver() {
        assertThat(gateway.parseWebhook("{}", "sig")).isEmpty();
    }
}
