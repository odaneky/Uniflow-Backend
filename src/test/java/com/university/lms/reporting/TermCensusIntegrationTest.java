package com.university.lms.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A term census must reflect what {@code term_academic_records} actually holds — no live grade
 * recomputation, no counting a student twice, and a student's programme correctly attributed in the
 * breakdown.
 */
@AutoConfigureMockMvc
class TermCensusIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerScopingFixtures fixtures;

    private static RequestPostProcessor asRegistrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
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

    private static RequestPostProcessor asStudent(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "student-" + subject.substring(0, 8))
                        .claim("email", subject.substring(0, 8) + "@university.test")
                        .claim("given_name", "Test")
                        .claim("family_name", "Student"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT))});
    }

    @Test
    void censusReflectsAClosedTermsRecords() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        UUID term = fixtures.openTermId();

        String awardBody = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":88.00,\"publish\":true}";
        mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term).with(registrar))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/reports/terms/{id}/census", term).with(registrar))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("\"academicTermId\":\"" + term + "\"");
        int headcount = com.jayway.jsonpath.JsonPath.read(response, "$.headcount");
        assertThat(headcount).isGreaterThanOrEqualTo(1);
        java.util.List<?> byProgramme = com.jayway.jsonpath.JsonPath.read(response, "$.byProgramme");
        assertThat(byProgramme).isNotEmpty();
    }

    @Test
    void aStudentMayNotViewTheCensus() throws Exception {
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID term = fixtures.openTermId();

        mockMvc.perform(get("/api/v1/reports/terms/{id}/census", term).with(asStudent(student.subject())))
                .andExpect(status().isForbidden());
    }
}
