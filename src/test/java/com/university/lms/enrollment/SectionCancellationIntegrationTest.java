package com.university.lms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.outbox.DomainOutboxRepository;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.enrollment.service.EnrollmentOutboxPublisher;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
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
 * Cancelling a section used to be nothing more than a status flip: enrolled students stayed
 * enrolled (and billed) in a course that no longer ran, with no notification and no seat release.
 */
@AutoConfigureMockMvc
class SectionCancellationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerScopingFixtures fixtures;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private DomainOutboxRepository domainOutboxRepository;

    private static RequestPostProcessor asRegistrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))});
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
    @DisplayName("cancelling a section drops every enrolled student and notifies them")
    void cancellingASectionDropsEnrolledStudents() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSection();
        UUID enrollmentId = fixtures.enrol(student.studentId(), section);

        String response = mockMvc.perform(post("/api/v1/courses/sections/{id}/cancel", section).with(registrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsAffected").value(1))
                .andExpect(jsonPath("$.seatsReleased").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).contains("\"courseSectionId\"");

        Enrollment enrolment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(enrolment.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);

        assertThat(domainOutboxRepository.findAll()).anyMatch(row ->
                row.getEventType().equals(EnrollmentOutboxPublisher.EVENT_SECTION_CANCELLED)
                        && row.getAggregateId().equals(enrollmentId));
    }

    @Test
    @DisplayName("a waitlisted student is dropped, not promoted, when their section is cancelled")
    void waitlistedStudentIsDroppedNotPromoted() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSection();
        UUID enrollmentId = enrollmentRepository
                .saveAndFlush(new Enrollment(student.studentId(), section, EnrollmentStatus.WAITLISTED))
                .getId();

        mockMvc.perform(post("/api/v1/courses/sections/{id}/cancel", section).with(registrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsAffected").value(1))
                // A waitlisted enrolment never held a seat, so none is released for it.
                .andExpect(jsonPath("$.seatsReleased").value(0));

        assertThat(enrollmentRepository.findById(enrollmentId).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.DROPPED);
    }

    @Test
    @DisplayName("a student may not cancel a section")
    void aStudentMayNotCancelASection() throws Exception {
        UUID section = fixtures.openSection();

        mockMvc.perform(post("/api/v1/courses/sections/{id}/cancel", section)
                        .with(asStudent(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }
}
