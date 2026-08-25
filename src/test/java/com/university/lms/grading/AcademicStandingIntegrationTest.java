package com.university.lms.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.domain.Programme;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.grading.domain.AcademicStanding;
import com.university.lms.grading.repository.AcademicStandingEventRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.repository.StudentRepository;
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
 * C7: closing a term must derive the student's standing from their cumulative GPA, record it as an
 * {@code AcademicStandingEvent}, and — for the two outcomes that are safe to drive automatically —
 * apply it to {@code students.status} through the existing {@code ALLOWED_TRANSITIONS} machinery.
 */
@AutoConfigureMockMvc
class AcademicStandingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicStandingEventRepository academicStandingEventRepository;

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

    private UUID sectionTaughtBy(AcademicTerm term, UUID lecturerUserId) {
        CourseSection section = academicFixtures.openSection(term, 50);
        section.assignLecturer(lecturerUserId);
        return courseSectionRepository.saveAndFlush(section).getId();
    }

    private void award(RequestPostProcessor as, UUID studentId, UUID sectionId, String percentage) throws Exception {
        String body = "{\"studentId\":\"" + studentId + "\",\"courseSectionId\":\"" + sectionId
                + "\",\"percentage\":" + percentage + ",\"publish\":true}";
        mockMvc.perform(post("/api/v1/grades").with(as).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void failingCumulativeGpaPlacesOnProbationAndRecoveryReturnsToActive() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person teacher = ownerScopingFixtures.lecturer();
        RequestPostProcessor lecturer = asLecturer(teacher.subject());

        Programme programme = academicFixtures.programme();
        Student student = academicFixtures.student(programme);

        AcademicTerm term1 = academicFixtures.openTerm();
        UUID section1 = sectionTaughtBy(term1, teacher.userId());
        award(lecturer, student.getId(), section1, "40.00"); // F, 0.00 grade points

        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term1.getId()).with(registrar))
                .andExpect(status().isOk());

        assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.PROBATION);
        var afterTerm1 = academicStandingEventRepository.findTopByStudentIdOrderByTermOrderDesc(student.getId());
        assertThat(afterTerm1).isPresent();
        assertThat(afterTerm1.get().getFromStanding()).isNull();
        assertThat(afterTerm1.get().getToStanding()).isEqualTo(AcademicStanding.PROBATION);

        // Two strong terms outweigh the one failing term's 3 credits (9 credits at 3.70 vs. 3 at
        // 0.00 clears the 2.00 cumulative floor) and should lift the student back to good standing.
        AcademicTerm term2 = academicFixtures.openTerm();
        UUID section2a = sectionTaughtBy(term2, teacher.userId());
        UUID section2b = sectionTaughtBy(term2, teacher.userId());
        award(lecturer, student.getId(), section2a, "85.00"); // A, 3.70 grade points
        award(lecturer, student.getId(), section2b, "85.00");

        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term2.getId()).with(registrar))
                .andExpect(status().isOk());

        assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.ACTIVE);
        var afterTerm2 = academicStandingEventRepository.findTopByStudentIdOrderByTermOrderDesc(student.getId());
        assertThat(afterTerm2).isPresent();
        assertThat(afterTerm2.get().getFromStanding()).isEqualTo(AcademicStanding.PROBATION);
        assertThat(afterTerm2.get().getToStanding()).isEqualTo(AcademicStanding.GOOD_STANDING);
    }

    @Test
    void aHealthyFirstTermNeverTouchesStatus() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        OwnerScopingFixtures.Person teacher = ownerScopingFixtures.lecturer();
        RequestPostProcessor lecturer = asLecturer(teacher.subject());

        Programme programme = academicFixtures.programme();
        Student student = academicFixtures.student(programme);
        AcademicTerm term = academicFixtures.openTerm();
        UUID section = sectionTaughtBy(term, teacher.userId());
        award(lecturer, student.getId(), section, "85.00");

        mockMvc.perform(post("/api/v1/academic-terms/{id}/close", term.getId()).with(registrar))
                .andExpect(status().isOk());

        assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.ACTIVE);
        var event = academicStandingEventRepository.findTopByStudentIdOrderByTermOrderDesc(student.getId());
        assertThat(event).isPresent();
        assertThat(event.get().getToStanding()).isEqualTo(AcademicStanding.GOOD_STANDING);
    }
}
