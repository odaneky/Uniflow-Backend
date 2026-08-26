package com.university.lms.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.academic.domain.Programme;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.dto.CreateRequirementBlockRequest;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentProgrammeEnrolment;
import com.university.lms.student.repository.StudentProgrammeEnrolmentRepository;
import com.university.lms.student.service.StudentProgrammeEnrolmentService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * M1's own acceptance test, in the shape the plan states it: snapshot a student's degree audit,
 * change the programme's requirements, re-run, and assert the historical answer is unchanged.
 *
 * <p>Before this, {@code student_programme_enrolments.curriculum_version_id} was always written
 * {@code null} and {@link CurriculumService#progressOf} always resolved against the programme's
 * current active version — so republishing silently rewrote every already-enrolled student's degree
 * audit. {@link CurriculumService#resolveVersionFor} is the fix: it binds a student to a version,
 * once, the first time their progress is resolved, through the write path {@link
 * com.university.lms.student.api.StudentProgrammeEnrolments} publishes for exactly this purpose.
 */
class CurriculumVersionBindingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private StudentProgrammeEnrolmentService programmeEnrolmentService;

    @Autowired
    private StudentProgrammeEnrolmentRepository programmeEnrolmentRepository;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Test
    @DisplayName("a student's first degree audit binds them to a version, and republishing does not move that answer")
    void resolvedVersionSurvivesARepublish() throws Exception {
        Programme programme = academicFixtures.programme();
        UUID programmeId = programme.getId();
        Student student = academicFixtures.student(programme);
        programmeEnrolmentService.openInitial(student.getId(), programmeId, LocalDate.now());

        RunAs.staff(() -> {
            curriculumService.createBlock(
                    programmeId, new CreateRequirementBlockRequest("Core V1", RequirementKind.CORE, 3, null, null));
            curriculumService.publishVersion(programmeId);
            return null;
        });

        // Nothing bound yet: no read has happened.
        assertThat(boundVersionOf(student.getId())).isNull();

        DegreeProgressResponse firstRead = RunAs.staff(() -> curriculumService.progressOf(student.getId()));
        assertThat(firstRead.blocks()).extracting(DegreeProgressResponse.RequirementProgressResponse::name)
                .containsExactly("Core V1");

        UUID boundVersionId = boundVersionOf(student.getId());
        assertThat(boundVersionId).isNotNull();

        // Republish: retire V1, publish a V2 with different requirements.
        RunAs.staff(() -> {
            curriculumService.createBlock(
                    programmeId, new CreateRequirementBlockRequest("Core V2", RequirementKind.CORE, 6, null, null));
            curriculumService.publishVersion(programmeId);
            return null;
        });

        // The student's binding must not have moved...
        assertThat(boundVersionOf(student.getId())).isEqualTo(boundVersionId);

        // ...and their degree audit must still show V1's requirements, not V2's.
        DegreeProgressResponse secondRead = RunAs.staff(() -> curriculumService.progressOf(student.getId()));
        assertThat(secondRead.blocks()).extracting(DegreeProgressResponse.RequirementProgressResponse::name)
                .containsExactly("Core V1");

        // A brand new student, never read before V2 existed, sees the current requirements instead.
        Student newStudent = academicFixtures.student(programme);
        programmeEnrolmentService.openInitial(newStudent.getId(), programmeId, LocalDate.now());
        DegreeProgressResponse newStudentRead = RunAs.staff(() -> curriculumService.progressOf(newStudent.getId()));
        assertThat(newStudentRead.blocks())
                .extracting(DegreeProgressResponse.RequirementProgressResponse::name)
                .containsExactly("Core V2");
    }

    private UUID boundVersionOf(UUID studentId) {
        return programmeEnrolmentRepository
                .findByStudentIdAndEndedOnIsNullAndPrimaryTrue(studentId)
                .map(StudentProgrammeEnrolment::getCurriculumVersionId)
                .orElse(null);
    }
}
