package com.university.lms.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.Arrays;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pins the authorization rules in {@code SecurityConfig} against the real application context.
 *
 * <p>These rules are an ordered list where the first match wins, which makes them quietly fragile:
 * inserting a broad pattern above a narrow one silently widens access, and nothing about the
 * change looks wrong in review. Every assertion here names a rule that a plausible edit could
 * break.
 *
 * <p>No Keycloak is involved. {@code jwt()} installs an already-authenticated principal carrying
 * the authorities under test, so the filter chain — including the rules themselves — runs exactly
 * as in production while token validation, which is tested separately, is bypassed.
 *
 * <p>Requests that are <em>expected to be allowed</em> are asserted as "not 401 and not 403"
 * rather than as a specific success code. Whether an empty or invented body then yields 201 or 400
 * is a question for the endpoint's own tests; here the only question is whether authorization let
 * the request through.
 */
@AutoConfigureMockMvc
class AuthorizationRulesIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor as(String... roles) {
        return jwt().authorities(Arrays.stream(roles)
                .map(SecurityRoles::authority)
                .map(SimpleGrantedAuthority::new)
                .toArray(GrantedAuthority[]::new));
    }

    /** Asserts the request got past the filter chain, whatever the endpoint then made of it. */
    private void allowed(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        Assertions.assertThat(result.getResponse().getStatus())
                .as("expected authorization to permit this request")
                .isNotIn(401, 403);
    }

    private void denied(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("health is reachable without a token — it is what tells the platform we are alive")
        void healthIsPublic() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("prometheus is not a public surface on the API port")
        void prometheusIsNotPublicOnTheApiPort() throws Exception {
            int status = mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getStatus();
            Assertions.assertThat(status)
                    .as("scrape must not be 200 on the public connector")
                    .isNotEqualTo(200);
            mockMvc.perform(get("/actuator/metrics")).andExpect(result ->
                    Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        }

        @Test
        @DisplayName("every API endpoint requires a token")
        void apiRequiresAToken() throws Exception {
            for (String path : new String[] {
                "/api/v1/courses", "/api/v1/students", "/api/v1/enrollments", "/api/v1/faculties", "/api/v1/users", "/api/v1/audit-events"
            }) {
                mockMvc.perform(get(path))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            }
        }

        @Test
        @DisplayName("an authenticated principal with no roles can read nothing privileged")
        void noRolesGrantsNothingPrivileged() throws Exception {
            denied(json(post("/api/v1/faculties").with(jwt()), "{}"));
        }
    }

    @Nested
    @DisplayName("User administration is SYSTEM_ADMIN only")
    class UserAdministration {

        @Test
        @DisplayName("reading users is privileged too — it is a roster of the whole institution")
        void readingUsersIsPrivileged() throws Exception {
            denied(get("/api/v1/users").with(as(SecurityRoles.REGISTRAR)));
            denied(get("/api/v1/users").with(as(SecurityRoles.LECTURER)));
            allowed(get("/api/v1/users").with(as(SecurityRoles.SYSTEM_ADMIN)));
        }

        @Test
        @DisplayName("granting a role is refused to everyone but SYSTEM_ADMIN")
        void grantingRolesIsSystemAdminOnly() throws Exception {
            String path = "/api/v1/users/" + UUID.randomUUID() + "/roles";
            denied(post(path).param("role", "SYSTEM_ADMIN").with(as(SecurityRoles.REGISTRAR)));
            denied(post(path).param("role", "SYSTEM_ADMIN").with(as(SecurityRoles.STUDENT)));
        }
    }

    @Nested
    @DisplayName("Academic structure and calendar")
    class AcademicAdministration {

        @Test
        @DisplayName("faculty administration may create structure; teaching staff may not")
        void structureIsAdministrative() throws Exception {
            allowed(json(post("/api/v1/faculties").with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            allowed(json(post("/api/v1/departments").with(as(SecurityRoles.SYSTEM_ADMIN)), "{}"));
            denied(json(post("/api/v1/faculties").with(as(SecurityRoles.LECTURER)), "{}"));
            denied(json(post("/api/v1/programmes").with(as(SecurityRoles.STUDENT)), "{}"));
        }

        /**
         * The registration window decides when students may begin competing for seats. It belongs
         * to the registry, and specifically not to faculty administration, which can otherwise
         * edit most academic structure.
         */
        @Test
        @DisplayName("only the registry opens registration")
        void registrationWindowIsRegistryOnly() throws Exception {
            String path = "/api/v1/academic-terms/" + UUID.randomUUID() + "/registration-window";
            allowed(json(put(path).with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.ACADEMIC_ADVISOR)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("only the registry opens add/drop")
        void addDropWindowIsRegistryOnly() throws Exception {
            String path = "/api/v1/academic-terms/" + UUID.randomUUID() + "/add-drop-window";
            allowed(json(put(path).with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("only the registry publishes a tuition installment plan")
        void paymentPlanIsRegistryOnly() throws Exception {
            String path = "/api/v1/payment-plans/" + UUID.randomUUID();
            allowed(json(put(path).with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            denied(json(put(path).with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("only the registry publishes the university credit-load default")
        void academicPolicyIsRegistryOnly() throws Exception {
            allowed(json(put("/api/v1/academic-policy").with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(put("/api/v1/academic-policy").with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            denied(json(put("/api/v1/academic-policy").with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("only a system admin may change campus branding")
        void brandingWriteIsSystemAdminOnly() throws Exception {
            allowed(json(put("/api/v1/branding").with(as(SecurityRoles.SYSTEM_ADMIN)), "{}"));
            denied(json(put("/api/v1/branding").with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(put("/api/v1/branding").with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(delete("/api/v1/branding").with(as(SecurityRoles.SYSTEM_ADMIN)));
            denied(delete("/api/v1/branding").with(as(SecurityRoles.REGISTRAR)));
        }

        @Test
        @DisplayName("anyone may read branding before sign-in")
        void brandingIsPublic() throws Exception {
            mockMvc.perform(get("/api/v1/branding")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("only the registry publishes the tuition schedule")
        void tuitionScheduleIsRegistryOnly() throws Exception {
            allowed(json(put("/api/v1/tuition-schedule").with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(put("/api/v1/tuition-schedule").with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            denied(json(put("/api/v1/tuition-schedule").with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("only the registry adds fees to the catalog")
        void feeCatalogWriteIsRegistryOnly() throws Exception {
            allowed(json(post("/api/v1/fee-catalog").with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(post("/api/v1/fee-catalog").with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
            denied(json(post("/api/v1/fee-catalog").with(as(SecurityRoles.STUDENT)), "{}"));
        }
    }

    @Nested
    @DisplayName("Course catalog")
    class Catalog {

        @Test
        @DisplayName("any authenticated member of the university may read the catalog")
        void catalogIsReadableByAll() throws Exception {
            allowed(get("/api/v1/courses").with(as(SecurityRoles.STUDENT)));
            allowed(get("/api/v1/courses").with(as(SecurityRoles.LECTURER)));
        }

        @Test
        @DisplayName("students cannot create courses or sections")
        void writingTheCatalogIsPrivileged() throws Exception {
            denied(json(post("/api/v1/courses").with(as(SecurityRoles.STUDENT)), "{}"));
            String sectionPath = "/api/v1/courses/" + UUID.randomUUID() + "/sections";
            denied(json(post(sectionPath).with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("replacing course requirements is a catalog write, not a student action")
        void replacingRequirementsIsPrivileged() throws Exception {
            String path = "/api/v1/courses/" + UUID.randomUUID() + "/requirements";
            denied(json(put(path).with(as(SecurityRoles.STUDENT)), "{\"groups\":[]}"));
            allowed(json(put(path).with(as(SecurityRoles.REGISTRAR)), "{\"groups\":[]}"));
        }

        /**
         * Section open/close lives under {@code /courses/**}, so it is covered by the catalog rule
         * rather than needing one of its own. Asserted because a future URL reshuffle that moved
         * these out from under {@code /courses} would drop them onto the broad authenticated
         * fallback without any rule appearing to change.
         */
        @Test
        @DisplayName("opening a section for enrolment is a catalog write, not a student action")
        void openingASectionIsPrivileged() throws Exception {
            String path = "/api/v1/courses/sections/" + UUID.randomUUID() + "/open";
            denied(post(path).with(as(SecurityRoles.STUDENT)));
            allowed(post(path).with(as(SecurityRoles.REGISTRAR)));
        }

        @Test
        @DisplayName("updating or removing a section is a catalog write, not a student action")
        void changingASectionIsPrivileged() throws Exception {
            String id = UUID.randomUUID().toString();
            denied(delete("/api/v1/courses/sections/" + id).with(as(SecurityRoles.STUDENT)));
            allowed(delete("/api/v1/courses/sections/" + id).with(as(SecurityRoles.REGISTRAR)));
            denied(post("/api/v1/courses/sections/" + id + "/cancel").with(as(SecurityRoles.STUDENT)));
            allowed(post("/api/v1/courses/sections/" + id + "/cancel").with(as(SecurityRoles.REGISTRAR)));
        }
    }

    @Nested
    @DisplayName("Enrolment")
    class Enrolment {

        @Test
        @DisplayName("students may enrol and drop")
        void studentsMayEnrolAndDrop() throws Exception {
            allowed(json(post("/api/v1/enrollments").with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(post("/api/v1/enrollments/" + UUID.randomUUID() + "/drop").with(as(SecurityRoles.STUDENT)));
        }

        /**
         * The ordering trap. {@code POST /enrollments/*}/complete} must be matched before the
         * general enrolment rule; if the broad rule were listed first it would win, and a student
         * could mark their own enrolment complete — awarding themselves the credit.
         */
        @Test
        @DisplayName("a student cannot complete their own enrolment")
        void completingIsNotAStudentAction() throws Exception {
            String path = "/api/v1/enrollments/" + UUID.randomUUID() + "/complete";
            denied(post(path).with(as(SecurityRoles.STUDENT)));
            allowed(post(path).with(as(SecurityRoles.LECTURER)));
            allowed(post(path).with(as(SecurityRoles.REGISTRAR)));
        }

        @Test
        @DisplayName("a lecturer is not an enrolment desk")
        void lecturersDoNotEnrolStudents() throws Exception {
            denied(json(post("/api/v1/enrollments").with(as(SecurityRoles.LECTURER)), "{}"));
        }
    }

    @Nested
    @DisplayName("Teaching writes")
    class Teaching {

        @Test
        @DisplayName("lecturers may publish material; students may not")
        void lecturersMayWriteLearning() throws Exception {
            String path = "/api/v1/learning/sections/" + UUID.randomUUID() + "/modules";
            allowed(json(post(path).with(as(SecurityRoles.LECTURER)), "{}"));
            denied(json(post(path).with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("awarding a grade is a teaching action")
        void studentsCannotAwardGrades() throws Exception {
            denied(json(post("/api/v1/grades").with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(json(post("/api/v1/grades").with(as(SecurityRoles.LECTURER)), "{}"));
        }

        @Test
        @DisplayName("the staff ledger is registry work, not a student action")
        void studentsCannotPostLedgerEntries() throws Exception {
            String path = "/api/v1/accounts/" + UUID.randomUUID() + "/entries";
            denied(json(post(path).with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(json(post(path).with(as(SecurityRoles.REGISTRAR)), "{}"));
        }

        @Test
        @DisplayName("A6: BURSAR may also post ledger entries, alongside REGISTRAR")
        void bursarMayPostLedgerEntries() throws Exception {
            String path = "/api/v1/accounts/" + UUID.randomUUID() + "/entries";
            allowed(json(post(path).with(as(SecurityRoles.BURSAR)), "{}"));
        }

        @Test
        @DisplayName(
                "A6: BURSAR may also administer payment plans, tuition rates and the fee catalog, "
                        + "alongside REGISTRAR — none of the three has a service-layer guard of its "
                        + "own, so SecurityConfig is the only gate")
        void bursarMayAdministerBillingConfiguration() throws Exception {
            allowed(json(
                    put("/api/v1/payment-plans/" + UUID.randomUUID()).with(as(SecurityRoles.BURSAR)), "{}"));
            allowed(json(put("/api/v1/tuition-schedule").with(as(SecurityRoles.BURSAR)), "{}"));
            allowed(json(
                    put("/api/v1/tuition-schedule/programmes/" + UUID.randomUUID()).with(as(SecurityRoles.BURSAR)),
                    "{}"));
            allowed(delete("/api/v1/tuition-schedule/programmes/" + UUID.randomUUID()).with(as(SecurityRoles.BURSAR)));
            allowed(json(post("/api/v1/fee-catalog").with(as(SecurityRoles.BURSAR)), "{}"));
            allowed(json(
                    patch("/api/v1/fee-catalog/" + UUID.randomUUID()).with(as(SecurityRoles.BURSAR)), "{}"));
            allowed(delete("/api/v1/fee-catalog/" + UUID.randomUUID()).with(as(SecurityRoles.BURSAR)));
        }

        @Test
        @DisplayName("billing configuration is registry work, not a student action")
        void studentsCannotAdministerBillingConfiguration() throws Exception {
            denied(json(put("/api/v1/tuition-schedule").with(as(SecurityRoles.STUDENT)), "{}"));
            denied(json(post("/api/v1/fee-catalog").with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("A6: FINANCIAL_AID_OFFICER may also administer financial aid, alongside REGISTRAR")
        void financialAidOfficerMayAdministerAid() throws Exception {
            allowed(post("/api/v1/financial-aid/isir/import")
                    .with(as(SecurityRoles.FINANCIAL_AID_OFFICER))
                    .content("studentId,ssn\n")
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN));
        }

        @Test
        @DisplayName("A6: ADMISSIONS_OFFICER may also work the admissions queue, alongside REGISTRAR")
        void admissionsOfficerMayClaimApplications() throws Exception {
            allowed(post("/api/v1/admissions/applications/" + UUID.randomUUID() + "/claim")
                    .with(as(SecurityRoles.ADMISSIONS_OFFICER)));
        }

        @Test
        @DisplayName("A6: EXAMS_OFFICER may also set the examination window, alongside REGISTRAR")
        void examsOfficerMaySetTheExamWindow() throws Exception {
            String path = "/api/v1/academic-terms/" + UUID.randomUUID() + "/exam-window";
            denied(json(put(path).with(as(SecurityRoles.LECTURER)), "{\"startsOn\":\"2026-12-01\",\"endsOn\":\"2026-12-14\"}"));
            allowed(json(
                    put(path).with(as(SecurityRoles.EXAMS_OFFICER)),
                    "{\"startsOn\":\"2026-12-01\",\"endsOn\":\"2026-12-14\"}"));
        }

        @Test
        @DisplayName("a student may pay their own account through /me")
        void studentsMayPayThemselves() throws Exception {
            allowed(json(post("/api/v1/me/account/payments").with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("a student may submit assessed work through /me")
        void studentsMaySubmitAssessments() throws Exception {
            allowed(post("/api/v1/me/assessments/" + UUID.randomUUID() + "/submissions")
                    .with(as(SecurityRoles.STUDENT)));
        }

        @Test
        @DisplayName("a student may file their own registry request through /me")
        void studentsMayFileOwnRequests() throws Exception {
            allowed(json(post("/api/v1/me/requests").with(as(SecurityRoles.STUDENT)), "{}"));
        }

        @Test
        @DisplayName("deciding a request is registry work, not a student action")
        void studentsCannotDecideRequests() throws Exception {
            String path = "/api/v1/requests/" + UUID.randomUUID() + "/decide";
            denied(json(post(path).with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(json(post(path).with(as(SecurityRoles.REGISTRAR)), "{}"));
        }

        @Test
        @DisplayName(
                "A6: FINANCIAL_AID_OFFICER reaches the requests gate, alongside REGISTRAR — "
                        + "the SAP_APPEAL-only decision is enforced deeper, in ServiceRequestWorkflow")
        void financialAidOfficerReachesTheRequestsGate() throws Exception {
            String path = "/api/v1/requests/" + UUID.randomUUID() + "/decide";
            allowed(json(post(path).with(as(SecurityRoles.FINANCIAL_AID_OFFICER)), "{}"));
        }

        @Test
        @DisplayName(
                "A6: ADMISSIONS_OFFICER may also provision a student record, alongside REGISTRAR — "
                        + "the fallback path AdmissionsService.matriculate falls through to when the "
                        + "identity provider is configured to create students directly")
        void admissionsOfficerMayProvisionAStudentRecord() throws Exception {
            String body = "{\"studentNumber\":\"20260001\",\"programmeId\":\"" + UUID.randomUUID()
                    + "\",\"admissionDate\":\"2025-09-01\"}";
            denied(json(post("/api/v1/students/provision").with(as(SecurityRoles.STUDENT)), body));
            allowed(json(post("/api/v1/students/provision").with(as(SecurityRoles.ADMISSIONS_OFFICER)), body));
        }

        @Test
        @DisplayName("curriculum writes sit with faculty administration")
        void studentsCannotWriteRequirementBlocks() throws Exception {
            String path = "/api/v1/programmes/" + UUID.randomUUID() + "/requirement-blocks";
            denied(json(post(path).with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(json(post(path).with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
        }

        @Test
        @DisplayName("amending a programme is not a student action")
        void studentsCannotPatchProgrammes() throws Exception {
            String path = "/api/v1/programmes/" + UUID.randomUUID();
            denied(json(patch(path).with(as(SecurityRoles.STUDENT)), "{}"));
            allowed(json(patch(path).with(as(SecurityRoles.FACULTY_ADMIN)), "{}"));
        }

        @Test
        @DisplayName("removing a requirement block is faculty administration")
        void studentsCannotDeleteRequirementBlocks() throws Exception {
            String path = "/api/v1/programmes/" + UUID.randomUUID() + "/requirement-blocks/" + UUID.randomUUID();
            denied(delete(path).with(as(SecurityRoles.STUDENT)));
            allowed(delete(path).with(as(SecurityRoles.REGISTRAR)));
        }
    }

    @Nested
    @DisplayName("Student records")
    class StudentRecords {

        @Test
        @DisplayName("only the registry creates or amends a student record")
        void writesAreRegistryOnly() throws Exception {
            allowed(json(post("/api/v1/students").with(as(SecurityRoles.REGISTRAR)), "{}"));
            denied(json(post("/api/v1/students").with(as(SecurityRoles.STUDENT)), "{}"));
            denied(json(post("/api/v1/students").with(as(SecurityRoles.LECTURER)), "{}"));
        }
    }

    @Nested
    @DisplayName("Audit trail")
    class AuditTrailAccess {

        /**
         * Listed before the catch-all authenticated GET. If that order inverted, every student
         * could enumerate the institution's privileged history.
         */
        @Test
        @DisplayName("the trail is registry work, not a student or lecturer read")
        void readingTheTrailIsPrivileged() throws Exception {
            denied(get("/api/v1/audit-events").with(as(SecurityRoles.STUDENT)));
            denied(get("/api/v1/audit-events").with(as(SecurityRoles.LECTURER)));
            denied(get("/api/v1/audit-events").with(as(SecurityRoles.FACULTY_ADMIN)));
            allowed(get("/api/v1/audit-events").with(as(SecurityRoles.REGISTRAR)));
            allowed(get("/api/v1/audit-events").with(as(SecurityRoles.SYSTEM_ADMIN)));
        }
    }

    @Nested
    @DisplayName("API documentation")
    class ApiDocumentation {

        /**
         * F7: the generated spec enumerates every endpoint's shape — not something to hand to every
         * authenticated caller by default, the same reasoning as the audit trail above.
         */
        @Test
        @DisplayName("the generated OpenAPI document is admin-only")
        void openApiDocumentIsSystemAdminOnly() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            denied(get("/v3/api-docs").with(as(SecurityRoles.REGISTRAR)));
            denied(get("/v3/api-docs").with(as(SecurityRoles.STUDENT)));
            allowed(get("/v3/api-docs").with(as(SecurityRoles.SYSTEM_ADMIN)));
        }

        @Test
        @DisplayName("the document generated for an admin is well-formed OpenAPI describing the real API")
        void openApiDocumentDescribesTheRealApi() throws Exception {
            mockMvc.perform(get("/v3/api-docs").with(as(SecurityRoles.SYSTEM_ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openapi").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/students']").exists());
        }
    }
}
