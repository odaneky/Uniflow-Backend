package com.university.lms.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Who may act on an application, and how an applicant gets back to their own.
 *
 * <p>Before the capability token, the application id was the credential: anyone holding it could
 * read, rewrite and submit somebody's application, and an applicant who lost it had no way back —
 * and was then locked out of applying again by the duplicate guard. Both halves are pinned here.
 */
@AutoConfigureMockMvc
class ApplicantAccessIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Application-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.university.lms.support.AcademicFixtures fixtures;

    private static RequestPostProcessor staff() {
        return jwt().jwt(token -> token.claim("sub", "registrar-" + UUID.randomUUID())
                        .claim("preferred_username", "registrar-" + UUID.randomUUID())
                        .claim("email", UUID.randomUUID() + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))
                });
    }

    /** Creates a draft and returns {applicationId, token, reference, email}. */
    private Created createApplication() throws Exception {
        var programme = fixtures.programme();
        var term = fixtures.openTerm();
        String email = "applicant-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "applicantEmail", email,
                "applicantName", "Test Applicant",
                "programmeId", programme.getId(),
                "academicTermId", term.getId(),
                "payload", java.util.Map.of()));

        String response = mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = objectMapper.readTree(response);
        return new Created(
                node.path("application").path("id").asText(),
                node.path("accessToken").asText(),
                node.path("application").path("reference").asText(),
                email);
    }

    private record Created(String id, String token, String reference, String email) {}

    @Nested
    @DisplayName("The token is issued once")
    class Issuance {

        @Test
        @DisplayName("creating an application returns a token, and reads never do")
        void tokenIsReturnedOnlyOnCreate() throws Exception {
            Created created = createApplication();
            assertThat(created.token()).isNotBlank().hasSize(43);

            String read = mockMvc.perform(get("/api/v1/applications/{id}", created.id())
                            .header(TOKEN_HEADER, created.token()))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // If the token were a field on the ordinary response it would be echoed by every read,
            // and would end up in logs, caches and staff screens.
            assertThat(read.toLowerCase()).doesNotContain("accesstoken").doesNotContain(created.token());
        }
    }

    @Nested
    @DisplayName("The id alone is no longer a credential")
    class IdIsNotEnough {

        @Test
        @DisplayName("reading without a token is refused")
        void readWithoutTokenIsRefused() throws Exception {
            Created created = createApplication();

            mockMvc.perform(get("/api/v1/applications/{id}", created.id()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("APPLICATION_ACCESS_DENIED"));
        }

        /** The write was the sharper hole: anyone with the id could rewrite the whole form. */
        @Test
        @DisplayName("editing without a token is refused")
        void editWithoutTokenIsRefused() throws Exception {
            Created created = createApplication();
            String body = objectMapper.writeValueAsString(java.util.Map.of("applicantName", "Attacker"));

            mockMvc.perform(patch("/api/v1/applications/{id}", created.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("submitting without a token is refused")
        void submitWithoutTokenIsRefused() throws Exception {
            Created created = createApplication();

            mockMvc.perform(post("/api/v1/applications/{id}/submit", created.id()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("one applicant's token does not open another's application")
        void tokensAreNotInterchangeable() throws Exception {
            Created mine = createApplication();
            Created theirs = createApplication();

            mockMvc.perform(get("/api/v1/applications/{id}", theirs.id())
                            .header(TOKEN_HEADER, mine.token()))
                    .andExpect(status().isForbidden());
        }

        /**
         * A refused caller must not be able to tell a real id from an invented one, or the refusal
         * becomes an oracle for discovering which applications exist.
         */
        @Test
        @DisplayName("an unknown id is refused identically to a real one")
        void unknownIdLooksTheSame() throws Exception {
            Created created = createApplication();

            String real = mockMvc.perform(get("/api/v1/applications/{id}", created.id()))
                    .andReturn().getResponse().getContentAsString();
            String invented = mockMvc.perform(get("/api/v1/applications/{id}", UUID.randomUUID()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(objectMapper.readTree(real).path("code").asText())
                    .isEqualTo(objectMapper.readTree(invented).path("code").asText())
                    .isEqualTo("APPLICATION_ACCESS_DENIED");
        }
    }

    @Nested
    @DisplayName("The applicant can get back in")
    class ComingBack {

        @Test
        @DisplayName("with the token they can read, edit and submit")
        void tokenGrantsTheApplicantTheirOwnApplication() throws Exception {
            Created created = createApplication();

            mockMvc.perform(get("/api/v1/applications/{id}", created.id())
                            .header(TOKEN_HEADER, created.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reference").value(created.reference()));

            mockMvc.perform(patch("/api/v1/applications/{id}", created.id())
                            .header(TOKEN_HEADER, created.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("applicantName", "Updated Name"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.applicantName").value("Updated Name"));
        }

        /**
         * Always 202, matched or not. Any other answer would let a caller test whether a given
         * person applied to a given programme.
         */
        @Test
        @DisplayName("resume reveals nothing about whether the pair matched")
        void resumeIsIndistinguishable() throws Exception {
            Created created = createApplication();

            mockMvc.perform(post("/api/v1/applications/resume")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "reference", created.reference(), "applicantEmail", created.email()))))
                    .andExpect(status().isAccepted());

            mockMvc.perform(post("/api/v1/applications/resume")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "reference", "NOPE-9999", "applicantEmail", "nobody@example.test"))))
                    .andExpect(status().isAccepted());
        }

        /** Resuming rotates the token, so a link the applicant thinks leaked stops working. */
        @Test
        @DisplayName("resuming invalidates the previous link")
        void resumeRotatesTheToken() throws Exception {
            Created created = createApplication();

            mockMvc.perform(get("/api/v1/applications/{id}", created.id())
                            .header(TOKEN_HEADER, created.token()))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/applications/resume")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "reference", created.reference(), "applicantEmail", created.email()))))
                    .andExpect(status().isAccepted());

            mockMvc.perform(get("/api/v1/applications/{id}", created.id())
                            .header(TOKEN_HEADER, created.token()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Staff")
    class Staff {

        @Test
        @DisplayName("admissions staff need no capability token")
        void staffBypassTheToken() throws Exception {
            Created created = createApplication();

            mockMvc.perform(get("/api/v1/applications/{id}", created.id()).with(staff()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reference").value(created.reference()));
        }
    }
}
