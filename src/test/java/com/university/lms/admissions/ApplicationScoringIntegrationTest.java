package com.university.lms.admissions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
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
 * G5: multiple reviewers can each score an application independently — the scores are shown
 * together, not aggregated into an automatic decision.
 */
@AutoConfigureMockMvc
class ApplicationScoringIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AcademicFixtures fixtures;

    private static RequestPostProcessor staffAs(String role, String label) {
        return jwt().jwt(token -> token.claim("sub", label + "-" + UUID.randomUUID())
                        .claim("preferred_username", label + "-" + UUID.randomUUID())
                        .claim("email", UUID.randomUUID() + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Test")
                        .claim("family_name", "Caller"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(role))});
    }

    private static RequestPostProcessor registrar() {
        return staffAs(SecurityRoles.REGISTRAR, "registrar");
    }

    private static RequestPostProcessor student() {
        return staffAs(SecurityRoles.STUDENT, "student");
    }

    private String createApplication() throws Exception {
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
        return node.path("application").path("id").asText();
    }

    @Test
    @DisplayName("two different reviewers each get their own score on the same application")
    void twoReviewersEachHaveTheirOwnScore() throws Exception {
        String applicationId = createApplication();

        mockMvc.perform(post("/api/v1/admissions/applications/{id}/scores", applicationId)
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("score", 4, "comment", "Strong"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(4));

        mockMvc.perform(post("/api/v1/admissions/applications/{id}/scores", applicationId)
                        .with(staffAs(SecurityRoles.SYSTEM_ADMIN, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(java.util.Map.of("score", 2, "comment", "Weak on essay"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("a reviewer resubmitting replaces their own score rather than adding a second one")
    void resubmittingReplacesTheReviewersOwnScore() throws Exception {
        String applicationId = createApplication();
        RequestPostProcessor sameReviewer = registrar();

        mockMvc.perform(post("/api/v1/admissions/applications/{id}/scores", applicationId)
                        .with(sameReviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("score", 3, "comment", "Initial"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admissions/applications/{id}/scores", applicationId)
                        .with(sameReviewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("score", 5, "comment", "Revised after interview"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].score").value(5))
                .andExpect(jsonPath("$[0].comment").value("Revised after interview"));
    }

    @Test
    @DisplayName("a score outside 1-5 is refused")
    void aScoreOutsideRangeIsRefused() throws Exception {
        String applicationId = createApplication();

        mockMvc.perform(post("/api/v1/admissions/applications/{id}/scores", applicationId)
                        .with(registrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("score", 9))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a student cannot score an application")
    void aStudentCannotScoreAnApplication() throws Exception {
        String applicationId = createApplication();

        mockMvc.perform(post("/api/v1/admissions/applications/{id}/scores", applicationId)
                        .with(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("score", 3))))
                .andExpect(status().isForbidden());
    }
}
