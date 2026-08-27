package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
 * E6: an invoice is a frozen snapshot of a term's posted charges — printable, emailable, and
 * addressable to a sponsor instead of the student — distinct from the running ledger.
 */
@AutoConfigureMockMvc
class InvoiceIntegrationTest extends AbstractPostgresIntegrationTest {

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

    private static RequestPostProcessor registrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))
                });
    }

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

    /** Posts real charges the way DefaultStudentBilling would, without going through registration. */
    private UUID postCharges(UUID studentId, UUID termId) {
        StudentAccount account = accountRepository.saveAndFlush(new StudentAccount(studentId, "USD"));
        entryRepository.saveAndFlush(new AccountEntry(
                account, AccountEntryType.CHARGE, new BigDecimal("1200.00"), "Tuition", Instant.now(), null, termId));
        entryRepository.saveAndFlush(new AccountEntry(
                account, AccountEntryType.CHARGE, new BigDecimal("75.00"), "Campus fee", Instant.now(), null, termId));
        return termId;
    }

    @Test
    @DisplayName("a registrar issues an invoice bundling a term's charges, then marks it paid")
    void issuingAndMarkingPaid() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        UUID termId = UUID.randomUUID();
        postCharges(student.studentId(), termId);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "academicTermId", termId, "dueOn", LocalDate.now().plusDays(30).toString()));

        String created = mockMvc.perform(post("/api/v1/accounts/{id}/invoices", student.studentId())
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(1275.00))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.lineItems.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String invoiceId = objectMapper.readTree(created).path("id").asText();

        mockMvc.perform(post("/api/v1/invoices/{id}/mark-paid", invoiceId).with(registrar()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/me/invoices").with(asStudent(student.subject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    @DisplayName("a sponsor-billed invoice carries who actually pays")
    void sponsorBilledInvoiceCarriesBillTo() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        UUID termId = UUID.randomUUID();
        postCharges(student.studentId(), termId);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "academicTermId", termId,
                "dueOn", LocalDate.now().plusDays(30).toString(),
                "billToName", "Acme Sponsorship Trust",
                "billToEmail", "billing@acme.test"));

        mockMvc.perform(post("/api/v1/accounts/{id}/invoices", student.studentId())
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.billToName").value("Acme Sponsorship Trust"))
                .andExpect(jsonPath("$.billToEmail").value("billing@acme.test"));
    }

    @Test
    @DisplayName("issuing against a term with no charges is refused")
    void issuingWithNoChargesIsRefused() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        accountRepository.saveAndFlush(new StudentAccount(student.studentId(), "USD"));

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("academicTermId", UUID.randomUUID(), "dueOn", LocalDate.now().plusDays(30).toString()));

        mockMvc.perform(post("/api/v1/accounts/{id}/invoices", student.studentId())
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NO_CHARGES"));
    }

    @Test
    @DisplayName("a voided invoice cannot be voided again")
    void aVoidedInvoiceCannotBeVoidedAgain() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        UUID termId = UUID.randomUUID();
        postCharges(student.studentId(), termId);
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "academicTermId", termId, "dueOn", LocalDate.now().plusDays(30).toString()));
        String created = mockMvc.perform(post("/api/v1/accounts/{id}/invoices", student.studentId())
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String invoiceId = objectMapper.readTree(created).path("id").asText();

        mockMvc.perform(post("/api/v1/invoices/{id}/void", invoiceId)
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("reason", "Duplicate"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"));

        mockMvc.perform(post("/api/v1/invoices/{id}/void", invoiceId)
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("reason", "Again"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("a student cannot issue an invoice")
    void aStudentCannotIssueAnInvoice() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        UUID termId = UUID.randomUUID();
        postCharges(student.studentId(), termId);
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "academicTermId", termId, "dueOn", LocalDate.now().plusDays(30).toString()));

        mockMvc.perform(post("/api/v1/accounts/{id}/invoices", student.studentId())
                        .with(asStudent(student.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
