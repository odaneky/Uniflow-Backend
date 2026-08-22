package com.university.lms.grading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ValidationException;
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
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private CurrentUserProvider currentUserProvider;

    @Mock
    private com.university.lms.administration.api.AuditTrail auditTrail;

    @Mock
    private com.university.lms.common.telemetry.UniFlowMetrics metrics;

    @Mock
    private GradeOutboxPublisher gradeOutboxPublisher;

    @Mock
    private com.university.lms.assessment.repository.AssessmentRepository assessmentRepository;

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

        Grade fail = new Grade(studentId, failSection, scale, new BigDecimal("40.00"), "F", new BigDecimal("0.00"));
        fail.publish();
        ReflectionTestUtils.setField(fail, "createdAt", Instant.parse("2024-01-01T00:00:00Z"));

        Grade pass = new Grade(studentId, passSection, scale, new BigDecimal("85.00"), "A", new BigDecimal("4.00"));
        pass.publish();
        ReflectionTestUtils.setField(pass, "createdAt", Instant.parse("2025-01-01T00:00:00Z"));

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

        Grade fail = new Grade(studentId, sectionId, scale, new BigDecimal("35.00"), "F", new BigDecimal("0.00"));
        fail.publish();
        ReflectionTestUtils.setField(fail, "createdAt", Instant.parse("2024-06-01T00:00:00Z"));

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
}
