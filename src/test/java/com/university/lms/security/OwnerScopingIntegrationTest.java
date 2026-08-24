package com.university.lms.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.financialaid.domain.AwardType;
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
 * Ownership: may this caller act on <em>this</em> record, not merely on records of this kind.
 *
 * <p>Role rules cannot answer that — a URL pattern does not know whose data is behind it — so every
 * assertion here is about the service layer consulting the caller's identity. Before this existed,
 * all of it was reachable: a STUDENT token could read any student's record, enrol a different
 * student, and drop someone else's enrolment. Those are the four scenarios below, and each one was
 * demonstrated against a running instance before the fix.
 *
 * <p>The caller is built with a {@code sub} claim matching a provisioned user, which is what the
 * production path resolves. {@code jwt()} skips token validation but not the resolution — the same
 * lookup runs here as in production.
 */
@AutoConfigureMockMvc
class OwnerScopingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerScopingFixtures fixtures;

    private static RequestPostProcessor asStudent(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "student-" + subject.substring(0, 8))
                        .claim("email", subject.substring(0, 8) + "@university.test")
                        .claim("given_name", "Test")
                        .claim("family_name", "Student"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT))});
    }

    /**
     * One Keycloak account has one stable subject and one email. Stable for the whole JVM run so
     * repeated calls resolve to the same user, but unique per run because the integration database
     * is reused — a fixed pair would collide with the row a previous run provisioned, and the
     * provisioning code would correctly reject that as an identity conflict.
     */
    private static final String REGISTRAR_SUBJECT = "registrar-" + UUID.randomUUID();

    private static RequestPostProcessor asRegistrar() {
        return jwt().jwt(token -> token.claim("sub", REGISTRAR_SUBJECT)
                        .claim("preferred_username", REGISTRAR_SUBJECT)
                        .claim("email", REGISTRAR_SUBJECT + "@university.test")
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))});
    }

    private static RequestPostProcessor asLecturer(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "lecturer-" + subject.substring(0, 8))
                        .claim("email", "lecturer-" + subject.substring(0, 8) + "@university.test")
                        .claim("given_name", "Lee")
                        .claim("family_name", "Lecturer"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.LECTURER))});
    }

    @Nested
    @DisplayName("Reading a student record")
    class Reads {

        @Test
        @DisplayName("a student may read their own record")
        void ownRecordIsReadable() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();

            mockMvc.perform(get("/api/v1/students/{id}", me.studentId()).with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(me.studentId().toString()));
        }

        @Test
        @DisplayName("a student may NOT read another student's record")
        void anotherStudentsRecordIsRefused() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            OwnerScopingFixtures.Person other = fixtures.student();

            mockMvc.perform(get("/api/v1/students/{id}", other.studentId()).with(asStudent(me.subject())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("looking someone up by student number is not a way around it")
        void byNumberIsScopedToo() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            OwnerScopingFixtures.Person other = fixtures.student();

            mockMvc.perform(get("/api/v1/students/by-number/{n}", other.studentNumber())
                            .with(asStudent(me.subject())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("staff may read any student record")
        void staffAreUnrestricted() throws Exception {
            OwnerScopingFixtures.Person someone = fixtures.student();

            mockMvc.perform(get("/api/v1/students/{id}", someone.studentId()).with(asRegistrar()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Enrolment")
    class Enrolments {

        /** The integrity half of the gap, and the sharper one: acting *as* another student. */
        @Test
        @DisplayName("a student may NOT enrol a different student")
        void cannotEnrolSomeoneElse() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            OwnerScopingFixtures.Person other = fixtures.student();
            UUID section = fixtures.openSection();

            String body = """
                    {"studentId":"%s","courseSectionId":"%s"}
                    """.formatted(other.studentId(), section);

            mockMvc.perform(post("/api/v1/enrollments")
                            .with(asStudent(me.subject()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("a student may enrol themselves")
        void canEnrolSelf() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            UUID section = fixtures.openSection();

            String body = """
                    {"studentId":"%s","courseSectionId":"%s"}
                    """.formatted(me.studentId(), section);

            mockMvc.perform(post("/api/v1/enrollments")
                            .with(asStudent(me.subject()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("a student may NOT drop another student's enrolment")
        void cannotDropSomeoneElsesEnrolment() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            OwnerScopingFixtures.Person other = fixtures.student();
            UUID enrolment = fixtures.enrol(other.studentId(), fixtures.openSection());

            mockMvc.perform(post("/api/v1/enrollments/{id}/drop", enrolment).with(asStudent(me.subject())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("a student may NOT read another student's enrolment")
        void cannotReadSomeoneElsesEnrolment() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            OwnerScopingFixtures.Person other = fixtures.student();
            UUID enrolment = fixtures.enrol(other.studentId(), fixtures.openSection());

            mockMvc.perform(get("/api/v1/enrollments/{id}", enrolment).with(asStudent(me.subject())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a student may drop their own enrolment")
        void canDropOwnEnrolment() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            UUID enrolment = fixtures.enrol(me.studentId(), fixtures.openSection());

            mockMvc.perform(post("/api/v1/enrollments/{id}/drop", enrolment).with(asStudent(me.subject())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("the assigned lecturer may complete an enrolment in their section")
        void assignedLecturerMayCompleteEnrolment() throws Exception {
            OwnerScopingFixtures.Person teacher = fixtures.lecturer();
            OwnerScopingFixtures.Person student = fixtures.student();
            UUID section = fixtures.openSectionTaughtBy(teacher.userId());
            UUID enrolment = fixtures.enrol(student.studentId(), section);
            publishOverallGrade(student.studentId(), section, asLecturer(teacher.subject()));

            mockMvc.perform(post("/api/v1/enrollments/{id}/complete", enrolment).with(asLecturer(teacher.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("a lecturer may NOT complete an enrolment in a section they do not teach")
        void otherLecturerMayNotCompleteEnrolment() throws Exception {
            OwnerScopingFixtures.Person teacher = fixtures.lecturer();
            OwnerScopingFixtures.Person other = fixtures.lecturer();
            OwnerScopingFixtures.Person student = fixtures.student();
            UUID section = fixtures.openSectionTaughtBy(teacher.userId());
            UUID enrolment = fixtures.enrol(student.studentId(), section);

            mockMvc.perform(post("/api/v1/enrollments/{id}/complete", enrolment).with(asLecturer(other.subject())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("the registry may complete an enrolment in any section")
        void registrarMayCompleteAnyEnrolment() throws Exception {
            OwnerScopingFixtures.Person teacher = fixtures.lecturer();
            OwnerScopingFixtures.Person student = fixtures.student();
            UUID section = fixtures.openSectionTaughtBy(teacher.userId());
            UUID enrolment = fixtures.enrol(student.studentId(), section);
            publishOverallGrade(student.studentId(), section, asRegistrar());

            mockMvc.perform(post("/api/v1/enrollments/{id}/complete", enrolment).with(asRegistrar()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        /**
         * Completing an enrolment now requires a published overall result to already exist — see
         * {@code EnrollmentService.complete}. Awarded through the real endpoint, not written
         * directly to the repository, so this exercises the same path production traffic does.
         */
        private void publishOverallGrade(UUID studentId, UUID sectionId, RequestPostProcessor as) throws Exception {
            mockMvc.perform(post("/api/v1/grades")
                            .with(as)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentId\":\"" + studentId + "\",\"courseSectionId\":\"" + sectionId
                                    + "\",\"percentage\":75.00,\"publish\":true}"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Finding out who you are")
    class Identity {

        /**
         * Without this a logged-in student is authenticated and anonymous: they hold no id they
         * could pass to any endpoint, so there is no route to their own data at all.
         */
        @Test
        @DisplayName("GET /api/v1/me resolves the token to a domain identity")
        void meResolvesTheCaller() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();

            mockMvc.perform(get("/api/v1/me").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(me.userId().toString()))
                    .andExpect(jsonPath("$.roles").isArray());
        }

        @Test
        @DisplayName("GET /api/v1/students/me returns the caller's own student record")
        void studentsMeReturnsOwnRecord() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();

            mockMvc.perform(get("/api/v1/students/me").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(me.studentId().toString()))
                    .andExpect(jsonPath("$.studentNumber").value(me.studentNumber()));
        }

        /**
         * A token whose subject has never been seen must become a usable identity on first sight.
         * Requiring an administrator to pre-create every row would mean nobody can use the system
         * until someone types them in, which defeats delegating authentication in the first place.
         */
        @Test
        @DisplayName("an unseen subject is provisioned on first request, not rejected")
        void unknownSubjectIsProvisionedJustInTime() throws Exception {
            String freshSubject = UUID.randomUUID().toString();

            mockMvc.perform(get("/api/v1/me").with(asStudent(freshSubject)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").exists())
                    .andExpect(jsonPath("$.externalIdentityId").value(freshSubject));
        }

        @Test
        @DisplayName("a provisioned user with no student record gets 404 from /students/me, not 500")
        void staffHaveNoStudentRecord() throws Exception {
            mockMvc.perform(get("/api/v1/students/me").with(asRegistrar()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        }
    }

    /**
     * {@code FinancialAidService.awardsForStudent} called only {@code requireStudentExists}, with no
     * check that the caller was staff or the student themselves. Every other personal-data read in
     * this suite is guarded; this one was not, and there was no test here to catch it.
     */
    @Nested
    @DisplayName("Financial aid")
    class FinancialAid {

        @Test
        @DisplayName("a student may read their own financial aid awards")
        void ownAwardsAreReadable() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            fixtures.financialAidAward(me.studentId(), AwardType.PELL, "3500.00");

            mockMvc.perform(get("/api/v1/financial-aid/students/{id}/awards", me.studentId())
                            .with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].studentId").value(me.studentId().toString()))
                    .andExpect(jsonPath("$[0].awardType").value("PELL"));
        }

        @Test
        @DisplayName("a student may not read another student's financial aid awards")
        void anotherStudentsAwardsAreRefused() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            OwnerScopingFixtures.Person other = fixtures.student();
            fixtures.financialAidAward(other.studentId(), AwardType.PELL, "3500.00");

            mockMvc.perform(get("/api/v1/financial-aid/students/{id}/awards", other.studentId())
                            .with(asStudent(me.subject())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("staff may read any student's financial aid awards")
        void staffAreUnrestricted() throws Exception {
            OwnerScopingFixtures.Person student = fixtures.student();
            fixtures.financialAidAward(student.studentId(), AwardType.INSTITUTIONAL, "1200.00");

            mockMvc.perform(get("/api/v1/financial-aid/students/{id}/awards", student.studentId())
                            .with(asRegistrar()))
                    .andExpect(status().isOk());
        }
    }

    /**
     * {@code StudentProvisioningService.provision} had no access-control check at all — neither
     * {@code SecurityConfig} (only the literal path {@code /api/v1/students} is role-gated, not
     * {@code /api/v1/students/provision}) nor the service layer. It fell through to the plain
     * {@code authenticated()} catch-all, so any signed-in student could mint an academic student
     * record for an arbitrary student number. Same shape as the financial-aid gap above.
     */
    @Nested
    @DisplayName("Provisioning a student record")
    class StudentProvisioning {

        @Test
        @DisplayName("a student may NOT provision a student record")
        void aStudentMayNotProvision() throws Exception {
            OwnerScopingFixtures.Person me = fixtures.student();
            String body = """
                    {"studentNumber":"900000123","programmeId":"%s","admissionDate":"2020-09-01"}
                    """.formatted(fixtures.programmeId());

            mockMvc.perform(post("/api/v1/students/provision")
                            .with(asStudent(me.subject()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("the registry may reach provisioning (rejected downstream only because the test identity provider is unavailable)")
        void theRegistryReachesProvisioning() throws Exception {
            String body = """
                    {"studentNumber":"900000124","programmeId":"%s","admissionDate":"2020-09-01"}
                    """.formatted(fixtures.programmeId());

            mockMvc.perform(post("/api/v1/students/provision")
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"));
        }
    }
}
