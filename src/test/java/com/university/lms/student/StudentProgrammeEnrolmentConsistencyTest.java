package com.university.lms.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.university.lms.academic.domain.Programme;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.student.domain.StudentProgrammeEnrolment;
import com.university.lms.student.repository.StudentProgrammeEnrolmentRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
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
 * {@code students.programme_id} and {@code student_programme_enrolments}' open primary row must
 * never disagree about a student's current programme — {@link
 * com.university.lms.student.service.StudentProgrammeEnrolmentService} exists specifically to keep
 * them from drifting apart, writing both in the same transaction as whatever changed one.
 */
@AutoConfigureMockMvc
class StudentProgrammeEnrolmentConsistencyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private StudentProgrammeEnrolmentRepository programmeEnrolmentRepository;

    private static RequestPostProcessor asRegistrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))});
    }

    @Test
    void creatingAStudentOpensAMatchingProgrammeEnrolment() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        Programme programme = academicFixtures.programme();
        UUID userId = academicFixtures.user().getId();

        String body = "{\"userId\":\"" + userId + "\",\"studentNumber\":\"" + studentNumber()
                + "\",\"programmeId\":\"" + programme.getId() + "\",\"admissionDate\":\"2020-09-01\"}";

        String response = mockMvc.perform(post("/api/v1/students")
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID studentId = UUID.fromString(JsonPath.read(response, "$.id"));

        StudentProgrammeEnrolment open = programmeEnrolmentRepository
                .findByStudentIdAndEndedOnIsNullAndPrimaryTrue(studentId)
                .orElseThrow();
        assertThat(open.getProgrammeId()).isEqualTo(programme.getId());
        assertThat(open.isPrimary()).isTrue();
        assertThat(open.isOpen()).isTrue();
    }

    @Test
    void transferringAStudentClosesTheOldRowAndOpensAMatchingNewOne() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        Programme oldProgramme = academicFixtures.programme();
        Programme newProgramme = academicFixtures.programme();
        UUID userId = academicFixtures.user().getId();

        String createBody = "{\"userId\":\"" + userId + "\",\"studentNumber\":\"" + studentNumber()
                + "\",\"programmeId\":\"" + oldProgramme.getId() + "\",\"admissionDate\":\"2020-09-01\"}";
        String response = mockMvc.perform(post("/api/v1/students")
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID studentId = UUID.fromString(JsonPath.read(response, "$.id"));

        mockMvc.perform(patch("/api/v1/students/{id}", studentId)
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programmeId\":\"" + newProgramme.getId() + "\"}"))
                .andExpect(status().isOk());

        StudentProgrammeEnrolment open = programmeEnrolmentRepository
                .findByStudentIdAndEndedOnIsNullAndPrimaryTrue(studentId)
                .orElseThrow();
        assertThat(open.getProgrammeId()).isEqualTo(newProgramme.getId());

        assertThat(programmeEnrolmentRepository.findByStudentIdOrderByStartedOnAsc(studentId)).hasSize(2);
    }

    private static String studentNumber() {
        return "9" + String.format("%09d", Math.abs(UUID.randomUUID().hashCode() % 1_000_000_000));
    }
}
