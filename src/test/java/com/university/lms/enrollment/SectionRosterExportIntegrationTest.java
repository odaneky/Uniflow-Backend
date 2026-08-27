package com.university.lms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * D8: the roster had no download — a registrar planning a graduation ceremony or an advisor
 * cross-checking a class list had to copy rows out of the screen by hand.
 */
@AutoConfigureMockMvc
class SectionRosterExportIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerScopingFixtures people;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private static RequestPostProcessor registrar() {
        return jwt().jwt(token -> token.claim("sub", "registrar-" + UUID.randomUUID())
                        .claim("preferred_username", "registrar")
                        .claim("email", "registrar@university.test")
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

    @Test
    @DisplayName("a registrar can download a section roster as CSV")
    void registrarCanDownloadTheRosterAsCsv() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        UUID sectionId = people.openSection();
        enrollmentRepository.saveAndFlush(new Enrollment(student.studentId(), sectionId));

        String body = mockMvc.perform(get("/api/v1/courses/sections/{id}/roster/export", sectionId)
                        .with(registrar()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body.lines().toList().get(0)).isEqualTo("Student Number,Full Name,Email,Status");
        assertThat(body).contains(student.studentNumber());
    }

    @Test
    @DisplayName("a student cannot download a section roster")
    void aStudentCannotDownloadTheRoster() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        UUID sectionId = people.openSection();

        mockMvc.perform(get("/api/v1/courses/sections/{id}/roster/export", sectionId)
                        .with(asStudent(student.subject())))
                .andExpect(status().isForbidden());
    }
}
