package com.university.lms.grading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.domain.GradeScale;
import com.university.lms.grading.domain.GradeScaleBand;
import com.university.lms.grading.domain.GradingErrorCode;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.GradeScaleBandRepository;
import com.university.lms.grading.repository.GradeScaleRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private GradeScaleRepository gradeScaleRepository;

    @Mock
    private GradeScaleBandRepository gradeScaleBandRepository;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private com.university.lms.administration.api.AuditTrail auditTrail;

    @Mock
    private com.university.lms.common.telemetry.UniFlowMetrics metrics;

    @Mock
    private GradeOutboxPublisher gradeOutboxPublisher;

    @Mock
    private com.university.lms.assessment.repository.AssessmentRepository assessmentRepository;

    @Mock
    private StaffAppointments staffAppointments;

    @InjectMocks
    private GradeService service;

    @Test
    void picksTheBandThatContainsThePercentage() {
        UUID scaleId = UUID.randomUUID();
        GradeScale scale = new GradeScale("Undergraduate Standard", "test");
        GradeScaleBand a = new GradeScaleBand(scale, "A", new BigDecimal("80.00"), new BigDecimal("89.99"), new BigDecimal("3.70"));
        GradeScaleBand b = new GradeScaleBand(scale, "B", new BigDecimal("70.00"), new BigDecimal("79.99"), new BigDecimal("3.00"));
        when(gradeScaleBandRepository.findByGradeScaleId(scaleId)).thenReturn(List.of(a, b));

        assertThat(service.bandFor(scaleId, new BigDecimal("82.00")).getLetter()).isEqualTo("A");
        assertThat(service.bandFor(scaleId, new BigDecimal("70.00")).getLetter()).isEqualTo("B");
    }

    @Test
    void refusesAPercentageNoBandCovers() {
        UUID scaleId = UUID.randomUUID();
        when(gradeScaleBandRepository.findByGradeScaleId(scaleId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.bandFor(scaleId, new BigDecimal("50.00")))
                .isInstanceOf(ValidationException.class)
                .satisfies(thrown -> assertThat(((ValidationException) thrown).getErrorCode())
                        .isEqualTo(GradingErrorCode.GRADE_BAND_NOT_FOUND));
    }

    @Test
    void summaryUsesMostRecentOverallPerCourseAndCreditsOnlyOnPass() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID failSection = UUID.randomUUID();
        UUID passSection = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        GradeScale scale = new GradeScale("Undergraduate Standard", "test");

        Grade fail = new Grade(
                studentId, failSection, scale, new BigDecimal("40.00"), "F", new BigDecimal("0.00"),
                courseId, termId, 3, 1);
        fail.publish();

        Grade pass = new Grade(
                studentId, passSection, scale, new BigDecimal("85.00"), "A", new BigDecimal("4.00"),
                courseId, termId, 3, 2);
        pass.publish();

        when(gradeRepository.findAllByStudentIdAndPublishedTrue(studentId)).thenReturn(List.of(fail, pass));
        when(courseCatalog.findSection(failSection))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        failSection, courseId, "HTM1001", "Intro", termId, "A", 30, 10, true, null, false)));
        when(courseCatalog.findSection(passSection))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        passSection, courseId, "HTM1001", "Intro", termId, "B", 30, 10, true, null, false)));
        when(courseCatalog.findCourse(courseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(
                        courseId, "HTM1001", "Intro", 3, 1, true)));
        when(enrollmentDirectory.accessibleSectionIds(studentId)).thenReturn(List.of(failSection, passSection));

        AcademicRecord.Summary summary = service.summaryOf(studentId);

        assertThat(summary.creditsEarned()).isEqualTo(3);
        assertThat(summary.gpa()).isEqualByComparingTo("4.00");
        assertThat(service.publishedOverallOf(studentId)).hasSize(2);
    }

    @Test
    void failedLatestSitEarnsNoCredits() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        GradeScale scale = new GradeScale("Undergraduate Standard", "test");

        Grade fail = new Grade(
                studentId, sectionId, scale, new BigDecimal("35.00"), "F", new BigDecimal("0.00"),
                courseId, termId, 3, 1);
        fail.publish();

        when(gradeRepository.findAllByStudentIdAndPublishedTrue(studentId)).thenReturn(List.of(fail));
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, courseId, "HTM1001", "Intro", termId, "A", 30, 10, true, null, false)));
        when(courseCatalog.findCourse(courseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(
                        courseId, "HTM1001", "Intro", 3, 1, true)));
        when(enrollmentDirectory.accessibleSectionIds(studentId)).thenReturn(List.of(sectionId));

        AcademicRecord.Summary summary = service.summaryOf(studentId);

        assertThat(summary.creditsEarned()).isZero();
        assertThat(summary.gpa()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("D8: the gradebook export includes one CSV row per grade with the student's name")
    void gradebookExportsAsCsv() {
        UUID sectionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        GradeScale scale = new GradeScale("Undergraduate Standard", "test");
        Grade grade = new Grade(
                studentId, sectionId, scale, new BigDecimal("88.50"), "A", new BigDecimal("3.70"),
                courseId, termId, 3, 1);

        CurrentUser registrar = new CurrentUser(
                UUID.randomUUID(), "sub", "registrar", "registrar@university.test", "Rita Registrar",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(registrar);
        when(courseCatalog.findSection(sectionId))
                .thenReturn(Optional.of(new CourseCatalog.SectionSummary(
                        sectionId, courseId, "CMP1010", "Foundations", termId, "A", 30, 10, true, null, false)));
        when(courseCatalog.findCourse(courseId))
                .thenReturn(Optional.of(new CourseCatalog.CourseSummary(courseId, "CMP1010", "Foundations", 3, 1, true)));
        when(academicStructure.findTerm(
                        org.mockito.ArgumentMatchers.eq(termId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(new AcademicStructure.TermSummary(
                        termId, "Fall", UUID.randomUUID(), "2026/2027", false)));
        when(gradeRepository.findByCourseSectionId(sectionId)).thenReturn(List.of(grade));
        when(enrollmentDirectory.attemptNumberOf(studentId, sectionId)).thenReturn(Optional.of(1));
        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, userId, "20260001", UUID.randomUUID(), null, true, ResidencyClassification.IN_DISTRICT)));
        when(userDirectory.findById(userId))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(userId, "student", "Sam Student", "sam@test", true)));

        String csv = service.exportGradebookCsv(sectionId);

        List<String> lines = csv.lines().toList();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("Student Number", "Student Name", "Letter");
        assertThat(lines.get(1)).contains("\"20260001\"", "\"Sam Student\"", "\"CMP1010\"", "\"A\"", "\"88.50\"");
    }
}
