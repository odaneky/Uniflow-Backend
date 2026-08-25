package com.university.lms.grading;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.grading.dto.BulkGradeUploadResponse;
import com.university.lms.grading.dto.CreateGradeRequest;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.service.BulkGradeUploadService;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.student.domain.Student;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D8: a 200-student section entered one mark at a time is the difference between usable and
 * unusable. Proves the upload saves every valid row, isolates one bad row from the rest, and a dry
 * run reports without writing anything.
 */
class BulkGradeUploadIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BulkGradeUploadService bulkGradeUploadService;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private OwnerScopingFixtures ownerScopingFixtures;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private GradeRepository gradeRepository;

    /** Assigns a freshly provisioned lecturer to the section and returns them, subject included. */
    private OwnerScopingFixtures.Person lecturerFor(CourseSection section) throws Exception {
        OwnerScopingFixtures.Person teacher = ownerScopingFixtures.lecturer();
        section.assignLecturer(teacher.userId());
        courseSectionRepository.saveAndFlush(section);
        return teacher;
    }

    private CreateGradeRequest gradeFor(UUID studentId, UUID sectionId, String percentage) {
        return new CreateGradeRequest(studentId, sectionId, null, null, new BigDecimal(percentage), true, null);
    }

    @Test
    @DisplayName("every valid row is saved")
    void savesEveryValidRow() throws Exception {
        AcademicTerm term = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(term, 50);
        OwnerScopingFixtures.Person teacher = lecturerFor(section);
        Student studentA = academicFixtures.student(academicFixtures.programme());
        Student studentB = academicFixtures.student(academicFixtures.programme());

        BulkGradeUploadResponse response = RunAs.as(
                teacher.subject(),
                SecurityRoles.LECTURER,
                () -> bulkGradeUploadService.upload(
                        List.of(
                                gradeFor(studentA.getId(), section.getId(), "80.00"),
                                gradeFor(studentB.getId(), section.getId(), "70.00")),
                        false));

        assertThat(response.succeeded()).isEqualTo(2);
        assertThat(response.failed()).isZero();
        assertThat(response.rows()).allSatisfy(row -> assertThat(row.outcome()).isEqualTo("SAVED"));
        assertThat(gradeRepository.findByCourseSectionId(section.getId())).hasSize(2);
    }

    @Test
    @DisplayName("a row for a nonexistent student fails without blocking the other rows")
    void oneBadRowDoesNotBlockTheOthers() throws Exception {
        AcademicTerm term = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(term, 50);
        OwnerScopingFixtures.Person teacher = lecturerFor(section);
        Student studentA = academicFixtures.student(academicFixtures.programme());
        UUID ghostStudentId = UUID.randomUUID();

        BulkGradeUploadResponse response = RunAs.as(
                teacher.subject(),
                SecurityRoles.LECTURER,
                () -> bulkGradeUploadService.upload(
                        List.of(
                                gradeFor(studentA.getId(), section.getId(), "80.00"),
                                gradeFor(ghostStudentId, section.getId(), "70.00")),
                        false));

        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(gradeRepository.findByCourseSectionId(section.getId())).hasSize(1);
    }

    @Test
    @DisplayName("a dry run reports without writing anything")
    void dryRunWritesNothing() throws Exception {
        AcademicTerm term = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(term, 50);
        OwnerScopingFixtures.Person teacher = lecturerFor(section);
        Student studentA = academicFixtures.student(academicFixtures.programme());
        UUID ghostStudentId = UUID.randomUUID();

        BulkGradeUploadResponse response = RunAs.as(
                teacher.subject(),
                SecurityRoles.LECTURER,
                () -> bulkGradeUploadService.upload(
                        List.of(
                                gradeFor(studentA.getId(), section.getId(), "80.00"),
                                gradeFor(ghostStudentId, section.getId(), "70.00")),
                        true));

        assertThat(response.dryRun()).isTrue();
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.rows().get(0).outcome()).isEqualTo("WOULD_SUCCEED");
        assertThat(response.rows().get(1).outcome()).isEqualTo("WOULD_FAIL");
        assertThat(gradeRepository.findByCourseSectionId(section.getId())).isEmpty();
    }
}
