package com.university.lms.student;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityConfig;
import com.university.lms.common.security.SecurityErrorResponder;
import com.university.lms.common.security.ClaimMappingProperties;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.common.security.TokenClaimReader;
import com.university.lms.student.domain.StudentErrorCode;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.dto.CreateStudentRequest;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.service.StudentService;
import com.university.lms.student.web.StudentController;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Verifies the HTTP contract of the student endpoints — status codes, the {@code Location} header,
 * and above all the shape of the error body.
 *
 * <p>The error assertions matter more than they look: the API's promise is that a client can
 * branch on {@code code} and read {@code errors[].field}, and never has to parse prose. If that
 * shape regresses, every consumer breaks silently, so it is pinned here.
 *
 * <p>Requests carry a stubbed JWT rather than a real one. {@code jwt()} installs an already
 * authenticated principal, so the whole filter chain runs — including the authorization rules
 * being asserted — without a Keycloak anywhere. The mocked {@link JwtDecoder} exists only because
 * {@code SecurityConfig} declares one; nothing in this class decodes a token.
 */
@WebMvcTest(controllers = StudentController.class)
@Import({SecurityConfig.class, SecurityErrorResponder.class, TokenClaimReader.class})
@EnableConfigurationProperties(ClaimMappingProperties.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StudentService studentService;

    @MockBean
    private JwtDecoder jwtDecoder;

    /** The controller also provisions academic records; not exercised here, but required to wire. */
    @MockBean
    private com.university.lms.student.service.StudentProvisioningService studentProvisioningService;

    /** Authorities are what the rules actually test; the rest of the token is irrelevant here. */
    private static RequestPostProcessor as(String... roles) {
        return jwt().authorities(java.util.Arrays.stream(roles)
                .map(SecurityRoles::authority)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new));
    }

    @Test
    @DisplayName("POST returns 201 with a Location header")
    void createReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();

        given(studentService.create(any(CreateStudentRequest.class)))
                .willReturn(new StudentResponse(
                        id,
                        userId,
                        "20260001",
                        programmeId,
                        StudentStatus.ACTIVE,
                        LocalDate.of(2025, 9, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now(),
                        null));

        String body = objectMapper.writeValueAsString(
                new CreateStudentRequest(userId, "20260001", programmeId, LocalDate.of(2025, 9, 1), null, null));

        mockMvc.perform(post("/api/v1/students")
                        .with(as(SecurityRoles.REGISTRAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/students/" + id))
                .andExpect(jsonPath("$.studentNumber").value("20260001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("a validation failure returns 400 in the standard error shape")
    void validationFailureReturnsStandardErrorBody() throws Exception {
        // Missing userId and programmeId; student number violates its pattern.
        String body =
                """
                {"userId": null, "studentNumber": "not valid!", "programmeId": null, "admissionDate": "2025-09-01"}
                """;

        mockMvc.perform(post("/api/v1/students")
                        .with(as(SecurityRoles.REGISTRAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/students"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'userId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'programmeId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'studentNumber')]").exists());
    }

    @Test
    @DisplayName("an unknown student returns 404 carrying the module's error code")
    void notFoundReturnsModuleErrorCode() throws Exception {
        UUID id = UUID.randomUUID();
        given(studentService.findById(id))
                .willThrow(new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + id));

        mockMvc.perform(get("/api/v1/students/{id}", id).with(as(SecurityRoles.REGISTRAR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("a malformed body is rejected without echoing the payload back")
    void malformedJsonIsRejectedSafely() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .with(as(SecurityRoles.REGISTRAR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    @Test
    @DisplayName("a non-UUID path variable is a 400, not a 500")
    void badPathVariableTypeIsClientError() throws Exception {
        mockMvc.perform(get("/api/v1/students/{id}", "not-a-uuid").with(as(SecurityRoles.REGISTRAR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    // ------------------------------------------------------------------
    // Authentication and authorization
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no token is 401 in the standard error envelope, not the container's error page")
    void missingTokenIsUnauthorisedInTheStandardShape() throws Exception {
        mockMvc.perform(get("/api/v1/students/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("a token without the required role is 403 in the standard error envelope")
    void insufficientRoleIsForbiddenInTheStandardShape() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateStudentRequest(
                UUID.randomUUID(), "20260001", UUID.randomUUID(), LocalDate.of(2025, 9, 1), null, null));

        mockMvc.perform(post("/api/v1/students")
                        .with(as(SecurityRoles.STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("a denied request never reaches the service")
    void deniedRequestDoesNotReachTheService() throws Exception {
        // The point of a filter-chain rule: rejection happens before any business code runs, so a
        // caller cannot use timing or side effects to learn anything about the data.
        String body = objectMapper.writeValueAsString(new CreateStudentRequest(
                UUID.randomUUID(), "20260002", UUID.randomUUID(), LocalDate.of(2025, 9, 1), null, null));

        mockMvc.perform(post("/api/v1/students")
                        .with(as(SecurityRoles.LECTURER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(studentService);
    }

    @Test
    @DisplayName("any authenticated role may read a student; only the registry may write one")
    void readsAreOpenToAnyAuthenticatedRoleButWritesAreNot() throws Exception {
        UUID id = UUID.randomUUID();
        given(studentService.findById(id))
                .willThrow(new ResourceNotFoundException(
                        StudentErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + id));

        // 404, not 403 — the request was authorised and got as far as the service.
        mockMvc.perform(get("/api/v1/students/{id}", id).with(as(SecurityRoles.STUDENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    /**
     * Regression test for a defect found by sending a junk token at a running instance.
     *
     * <p>Configuring only the global {@code exceptionHandling} entry point is not enough: the
     * resource-server configurer installs its own, which wins for anything the bearer-token filter
     * rejects — every malformed, expired or wrong-audience token, which is nearly all of them in
     * practice. The symptom was a 401 with {@code Content-Length: 0} and a {@code WWW-Authenticate}
     * header naming the exact decode failure. Only a request with no {@code Authorization} header
     * at all took the path that worked, which is precisely the case a quick manual check tries.
     */
    @Test
    @DisplayName("an invalid token is 401 in the standard envelope, with no reason disclosed")
    void invalidTokenIsUnauthorisedInTheStandardShape() throws Exception {
        given(jwtDecoder.decode(org.mockito.ArgumentMatchers.anyString()))
                .willThrow(new org.springframework.security.oauth2.jwt.BadJwtException("Malformed token"));

        mockMvc.perform(get("/api/v1/students/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").exists())
                // The decode reason is an oracle: it lets an unauthenticated caller distinguish
                // "expired" from "bad signature" from "wrong audience" while probing.
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }
}
