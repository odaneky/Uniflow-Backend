package com.university.lms.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.domain.Programme;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.curriculum.domain.DegreeAward;
import com.university.lms.curriculum.domain.Honours;
import com.university.lms.curriculum.repository.DegreeAwardRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.repository.StudentRepository;
import com.university.lms.student.service.StudentProgrammeEnrolmentService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.time.LocalDate;
import java.util.List;
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
 * G3: conferring a degree used to only flip {@code students.status} to {@code GRADUATED} — no
 * conferral date, GPA, curriculum version or honours were ever recorded. Proves {@link
 * DegreeAudit#recordConferral} writes that evidence and drives the status flip in one step, refuses
 * a second conferral for the same programme, and computes honours from the cumulative GPA at the
 * moment of conferral.
 */
@AutoConfigureMockMvc
class DegreeConferralIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DegreeAudit degreeAudit;

    @Autowired
    private DegreeAwardRepository degreeAwardRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentProgrammeEnrolmentService programmeEnrolmentService;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    private static RequestPostProcessor asLecturer(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "lecturer-" + subject.substring(0, 8))
                        .claim("email", "lecturer-" + subject.substring(0, 8) + "@university.test")
                        .claim("given_name", "Lee")
                        .claim("family_name", "Lecturer"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.LECTURER))});
    }

    @Test
    @DisplayName("recording a conferral snapshots GPA, credits and honours, and flips status to GRADUATED")
    void recordingConferralSnapshotsEvidenceAndGraduates() throws Exception {
        Programme programme = academicFixtures.programme();
        Student student = academicFixtures.student(programme);
        programmeEnrolmentService.openInitial(student.getId(), programme.getId(), LocalDate.now());

        OwnerScopingFixtures.Person teacher = ownerScopingFixtures.lecturer();
        RequestPostProcessor lecturer = asLecturer(teacher.subject());
        AcademicTerm term = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(term, 50);
        section.assignLecturer(teacher.userId());
        courseSectionRepository.saveAndFlush(section);

        String awardBody = "{\"studentId\":\"" + student.getId() + "\",\"courseSectionId\":\"" + section.getId()
                + "\",\"percentage\":95.00,\"publish\":true}";
        mockMvc.perform(post("/api/v1/grades")
                        .with(lecturer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody))
                .andExpect(status().isCreated());

        UUID actorId = teacher.userId();
        RunAs.staff(() -> {
            degreeAudit.recordConferral(student.getId(), actorId);
            return null;
        });

        List<DegreeAward> awards = degreeAwardRepository.findByStudentIdOrderByConferredOnDesc(student.getId());
        assertThat(awards).hasSize(1);
        DegreeAward award = awards.get(0);
        assertThat(award.getProgrammeId()).isEqualTo(programme.getId());
        assertThat(award.getDegreeAwardLabel()).isEqualTo(programme.getDegreeAward());
        assertThat(award.getGpaAtConferral()).isEqualByComparingTo("4.00");
        assertThat(award.getHonours()).isEqualTo(Honours.SUMMA_CUM_LAUDE);
        assertThat(award.getConferredBy()).isEqualTo(actorId);

        assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus())
                .isEqualTo(StudentStatus.GRADUATED);
    }

    @Test
    @DisplayName("a second conferral for the same programme is refused")
    void secondConferralForSameProgrammeRefused() throws Exception {
        Programme programme = academicFixtures.programme();
        Student student = academicFixtures.student(programme);
        programmeEnrolmentService.openInitial(student.getId(), programme.getId(), LocalDate.now());
        UUID actorId = ownerScopingFixtures.lecturer().userId();

        RunAs.staff(() -> {
            degreeAudit.recordConferral(student.getId(), actorId);
            return null;
        });

        assertThatThrownBy(() -> RunAs.staff(() -> {
                    degreeAudit.recordConferral(student.getId(), actorId);
                    return null;
                }))
                .isInstanceOf(ResourceAlreadyExistsException.class);
    }
}
