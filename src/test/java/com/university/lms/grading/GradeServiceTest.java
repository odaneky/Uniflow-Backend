package com.university.lms.grading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ValidationException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.grading.domain.GradeScale;
import com.university.lms.grading.domain.GradeScaleBand;
import com.university.lms.grading.domain.GradingErrorCode;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.GradeScaleBandRepository;
import com.university.lms.grading.repository.GradeScaleRepository;
import com.university.lms.grading.service.GradeService;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private com.university.lms.administration.api.AuditTrail auditTrail;

    @Mock
    private com.university.lms.common.telemetry.UniFlowMetrics metrics;

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
}
