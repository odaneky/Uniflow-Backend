package com.university.lms.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.TermAcademicRecordRepository;
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
 * Closing a term must lock every published overall grade in it and write exactly one {@code
 * TermAcademicRecord} per student — and running it twice on the same term must be a safe no-op,
 * since a registrar retrying after a partial failure (or simply clicking twice) is a normal thing
 * to happen.
 */
@AutoConfigureMockMvc
class TermCloseIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerScopingFixtures fixtures;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private TermAcademicRecordRepository termAcademicRecordRepository;

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

    @Test
    void closingATermLocksGradesAndWritesOneRecordPerStudent() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        UUID term = fixtures.openTermId();

        String awardBody = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":85.00,\"publish\":true}";
        String response = mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID gradeId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id"));

        // OwnerScopingFixtures.openTermId() is shared scenery across the whole test run, so other
        // students may already have been recorded for it by earlier tests — assert this test's own
        // student and grade, not the response's aggregate counts.
        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term).with(registrar))
                .andExpect(status().isOk());

        Grade grade = gradeRepository.findById(gradeId).orElseThrow();
        assertThat(grade.isLocked()).isTrue();

        assertThat(termAcademicRecordRepository.existsByStudentIdAndAcademicTermId(student.studentId(), term))
                .isTrue();
        var record = termAcademicRecordRepository
                .findByStudentIdOrderByTermOrderAsc(student.studentId())
                .get(0);
        assertThat(record.getCreditsAttempted()).isGreaterThan(0);
        assertThat(record.getTermGpa()).isNotNull();
    }

    @Test
    void closingAnAlreadyClosedTermIsANoOp() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        UUID term = fixtures.openTermId();

        String awardBody = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":70.00,\"publish\":true}";
        mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term).with(registrar))
                .andExpect(status().isOk());

        // Re-closing must not touch this student's record a second time — it already exists.
        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term).with(registrar))
                .andExpect(status().isOk());

        assertThat(termAcademicRecordRepository.findByStudentIdOrderByTermOrderAsc(student.studentId())).hasSize(1);
    }

    @Test
    void aLockedGradeRefusesRevisionAfterTermClose() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        UUID term = fixtures.openTermId();

        String awardBody = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":60.00,\"publish\":true}";
        mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term).with(registrar))
                .andExpect(status().isOk());

        String reviseBody = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":95.00,\"reason\":\"Trying to change a grade after term close\"}";
        mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviseBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GRADE_LOCKED"));
    }
}
