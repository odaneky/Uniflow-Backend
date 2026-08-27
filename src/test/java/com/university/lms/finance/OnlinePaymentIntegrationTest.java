package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.gateway.PaymentGateway;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * E7: this environment has no real Stripe account, so what's provable end to end is exactly what a
 * deployment that has not configured one should see — a clear refusal, never a "payment" that
 * quietly succeeds with no gateway behind it. {@code OnlinePaymentServiceTest} covers the settle/
 * fail/idempotency logic itself against a fake {@link PaymentGateway}.
 */
@AutoConfigureMockMvc
class OnlinePaymentIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerScopingFixtures people;

    @Autowired
    private StudentAccountRepository accountRepository;

    @Autowired
    private AccountEntryRepository entryRepository;

    @Autowired
    private PaymentGateway paymentGateway;

    private static RequestPostProcessor asStudent(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "s-" + subject.substring(0, 8))
                        .claim("email", subject.substring(0, 8) + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Test")
                        .claim("family_name", "Student"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT))
                });
    }

    @Test
    @DisplayName("without a configured gateway, this environment refuses rather than faking a payment")
    void noGatewayIsConfiguredByDefault() {
        assertThat(paymentGateway.configured()).isFalse();
    }

    @Test
    @DisplayName("a student cannot start an online payment when no gateway is configured")
    void initiatingWithNoGatewayConfiguredIsRefused() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        StudentAccount account = accountRepository.saveAndFlush(new StudentAccount(student.studentId(), "USD"));
        entryRepository.saveAndFlush(new AccountEntry(
                account, AccountEntryType.CHARGE, new BigDecimal("500.00"), "Tuition", Instant.now()));

        mockMvc.perform(post("/api/v1/me/account/payments/online")
                        .with(asStudent(student.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("amount", "200.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("the webhook endpoint always answers 200, even for a payload it cannot make sense of")
    void theWebhookEndpointNeverLeaksWhetherAReferenceWasValid() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "not-a-real-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("only the student themselves can start their own online payment")
    void onlyTheStudentCanStartTheirOwnPayment() throws Exception {
        mockMvc.perform(post("/api/v1/me/account/payments/online")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("amount", "10.00"))))
                .andExpect(status().isUnauthorized());
    }
}
