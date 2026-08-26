package com.university.lms.admissions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
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
 * G5: an attached application document had no verification state at all — admissions staff could
 * see it was uploaded but had no way to record that they had actually checked it.
 */
@AutoConfigureMockMvc
class ApplicationDocumentVerificationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Application-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AcademicFixtures fixtures;

    @Autowired
    private DocumentStore documentStore;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    private static RequestPostProcessor registrar() {
        return staffAs(SecurityRoles.REGISTRAR, "registrar");
    }

    private static RequestPostProcessor student() {
        return staffAs(SecurityRoles.STUDENT, "student");
    }

    private static RequestPostProcessor staffAs(String role, String label) {
        return jwt().jwt(token -> token.claim("sub", label + "-" + UUID.randomUUID())
                        .claim("preferred_username", label + "-" + UUID.randomUUID())
                        .claim("email", UUID.randomUUID() + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Test")
                        .claim("family_name", "Caller"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(role))});
    }

    private String createApplicationAndAttachDocument() throws Exception {
        var programme = fixtures.programme();
        var term = fixtures.openTerm();
        String email = "applicant-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "applicantEmail", email,
                "applicantName", "Test Applicant",
                "programmeId", programme.getId(),
                "academicTermId", term.getId(),
                "payload", java.util.Map.of()));

        String created = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = objectMapper.readTree(created);
        String applicationId = node.path("application").path("id").asText();
        String token = node.path("accessToken").asText();

        DocumentStore.StoredFile stored = documentStore.store(
                ownerScopingFixtures.lecturer().userId(),
                "TRANSCRIPT",
                "transcript.pdf",
                "application/pdf",
                "not a real pdf".getBytes());

        mockMvc.perform(post("/api/v1/applications/{id}/documents", applicationId)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("documentId", stored.id().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("PENDING"));

        return applicationId + ":" + stored.id();
    }

    @Test
    @DisplayName("a registrar can verify an attached document")
    void registrarCanVerifyADocument() throws Exception {
        String[] ids = createApplicationAndAttachDocument().split(":");

        mockMvc.perform(post(
                                "/api/v1/admissions/applications/{id}/documents/{documentId}/verify",
                                ids[0],
                                ids[1])
                        .with(registrar()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.documents[0].verifiedBy").isNotEmpty());
    }

    @Test
    @DisplayName("a registrar can reject an attached document with a reason")
    void registrarCanRejectADocument() throws Exception {
        String[] ids = createApplicationAndAttachDocument().split(":");

        mockMvc.perform(post(
                                "/api/v1/admissions/applications/{id}/documents/{documentId}/reject",
                                ids[0],
                                ids[1])
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("reason", "Illegible scan — please resubmit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.documents[0].rejectionReason").value("Illegible scan — please resubmit"));
    }

    @Test
    @DisplayName("a document already decided cannot be decided again")
    void aDecidedDocumentCannotBeDecidedAgain() throws Exception {
        String[] ids = createApplicationAndAttachDocument().split(":");

        mockMvc.perform(post(
                                "/api/v1/admissions/applications/{id}/documents/{documentId}/verify",
                                ids[0],
                                ids[1])
                        .with(registrar()))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                                "/api/v1/admissions/applications/{id}/documents/{documentId}/verify",
                                ids[0],
                                ids[1])
                        .with(registrar()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_DOCUMENT_ALREADY_DECIDED"));
    }

    @Test
    @DisplayName("a student cannot verify a document")
    void aStudentCannotVerifyADocument() throws Exception {
        String[] ids = createApplicationAndAttachDocument().split(":");

        mockMvc.perform(post(
                                "/api/v1/admissions/applications/{id}/documents/{documentId}/verify",
                                ids[0],
                                ids[1])
                        .with(student()))
                .andExpect(status().isForbidden());
    }
}
