package com.university.lms.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The filter, end to end, through the real chain.
 *
 * <p>Rate limiting is off for the rest of the suite — a shared per-address allowance would make
 * every other test's result depend on how many ran before it. This class switches it back on with a
 * deliberately tiny limit, and its own property set gives it a separate application context, so the
 * counter starts empty and nothing here leaks into another test.
 *
 * <p>The rule below is index 0, which is evaluated first, so it wins regardless of what the shipped
 * configuration contains.
 */
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "lms.rate-limit.enabled=true",
            "lms.rate-limit.rules[0].name=test-limit",
            "lms.rate-limit.rules[0].method=GET",
            "lms.rate-limit.rules[0].path=/api/v1/students/**",
            "lms.rate-limit.rules[0].limit=3",
            "lms.rate-limit.rules[0].window=5m",
        })
class RateLimitFilterIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Each test calls from its own address.
     *
     * <p>Buckets are keyed by rule and client, and MockMvc otherwise reports every request as
     * 127.0.0.1 — so two tests in this class would share one allowance and whichever ran second
     * would start already exhausted. Giving each its own address makes them independent, and
     * incidentally proves the keying isolates clients.
     */
    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /**
     * Asserted without a token on purpose. Rejection happens before authentication, so a flood is
     * turned away before token parsing or a database connection is involved — limiting after that
     * work protects the database but not the application in front of it.
     */
    @Test
    @DisplayName("refuses past the limit, before authentication, in the standard error envelope")
    void refusesPastTheLimitBeforeAuthentication() throws Exception {
        String path = "/api/v1/students/" + UUID.randomUUID();

        for (int i = 1; i <= 3; i++) {
            // Unauthenticated, so 401 — the point is that it is *not* 429 yet.
            mockMvc.perform(get(path).with(from("203.0.113.10"))).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get(path).with(from("203.0.113.10")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * A caller must not be able to map the thresholds from the refusal and pace themselves just
     * underneath it.
     */
    @Test
    @DisplayName("the refusal discloses neither the rule nor the remaining allowance")
    void refusalDisclosesNothingUseful() throws Exception {
        String path = "/api/v1/students/" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get(path).with(from("203.0.113.11")));
        }

        String body = mockMvc.perform(get(path).with(from("203.0.113.11")))
                .andExpect(status().isTooManyRequests())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .toLowerCase();

        for (String leaked : new String[] {"test-limit", "remaining", "quota", "bucket", "rule"}) {
            org.assertj.core.api.Assertions.assertThat(body).doesNotContain(leaked);
        }
    }
}
