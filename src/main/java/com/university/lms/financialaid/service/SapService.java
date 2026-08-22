package com.university.lms.financialaid.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.domain.SapEvaluation;
import com.university.lms.financialaid.dto.SapEvaluationResponse;
import com.university.lms.financialaid.repository.SapEvaluationRepository;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.grading.domain.GradeResult;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satisfactory Academic Progress evaluation.
 *
 * <p><b>Integration hook:</b> call {@link #evaluateAfterGrades(UUID, UUID)} from the grading module
 * after overall grades are published for a term — for example at the end of
 * {@code GradeService.publish} or via an outbox handler on {@code GRADE_PUBLISHED} events once all
 * section grades for the term are final.
 */
@Service
@Transactional(readOnly = true)
public class SapService {

    private static final BigDecimal MIN_GPA = new BigDecimal("2.00");
    private static final BigDecimal MIN_COMPLETION_RATE = new BigDecimal("0.6700");

    private final SapEvaluationRepository repository;
    private final AcademicRecord academicRecord;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseCatalog courseCatalog;
    private final GradeRepository gradeRepository;
    private final StudentDirectory studentDirectory;
    private final AcademicStructure academicStructure;
    private final ServiceHoldService serviceHoldService;

    public SapService(
            SapEvaluationRepository repository,
            AcademicRecord academicRecord,
            EnrollmentRepository enrollmentRepository,
            CourseCatalog courseCatalog,
            GradeRepository gradeRepository,
            StudentDirectory studentDirectory,
            AcademicStructure academicStructure,
            ServiceHoldService serviceHoldService) {
        this.repository = repository;
        this.academicRecord = academicRecord;
        this.enrollmentRepository = enrollmentRepository;
        this.courseCatalog = courseCatalog;
        this.gradeRepository = gradeRepository;
        this.studentDirectory = studentDirectory;
        this.academicStructure = academicStructure;
        this.serviceHoldService = serviceHoldService;
    }

    @Transactional
    public SapEvaluationResponse evaluateAfterGrades(UUID studentId, UUID academicTermId) {
        requireStudent(studentId);
        requireTerm(academicTermId);

        AcademicRecord.Summary summary = academicRecord.summaryOf(studentId);
        BigDecimal gpa = summary.gpa();
        BigDecimal completionRate = completionRateForTerm(studentId, academicTermId);
        boolean meetsSap = meetsSap(gpa, completionRate);
        Instant evaluatedAt = Instant.now();

        SapEvaluation saved = repository.save(new SapEvaluation(
                studentId, academicTermId, gpa, completionRate, meetsSap, evaluatedAt));

        if (!meetsSap) {
            serviceHoldService.placeHoldInternal(
                    studentId,
                    HoldType.SAP,
                    "SAP evaluation failed — GPA "
                            + formatGpa(gpa)
                            + ", completion rate "
                            + formatRate(completionRate),
                    null);
        }

        return SapEvaluationResponse.from(saved);
    }

    public SapEvaluationResponse latestForStudent(UUID studentId, UUID academicTermId) {
        return repository
                .findFirstByStudentIdAndAcademicTermIdOrderByEvaluatedAtDesc(studentId, academicTermId)
                .map(SapEvaluationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.SAP_EVALUATION_NOT_FOUND,
                        "No SAP evaluation exists for student " + studentId + " in term " + academicTermId));
    }

    private BigDecimal completionRateForTerm(UUID studentId, UUID academicTermId) {
        List<EnrollmentStatus> counted =
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.COMPLETED, EnrollmentStatus.WITHDRAWN);
        int attemptedCredits = 0;
        int earnedCredits = 0;
        for (var enrollment : enrollmentRepository.findByStudentIdAndStatusIn(studentId, counted)) {
            var section = courseCatalog.findSection(enrollment.getCourseSectionId());
            if (section.isEmpty() || !section.get().academicTermId().equals(academicTermId)) {
                continue;
            }
            int credits = courseCatalog
                    .findCourse(section.get().courseId())
                    .map(CourseCatalog.CourseSummary::credits)
                    .orElse(0);
            attemptedCredits += credits;
            if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
                var grade = gradeRepository.findByStudentIdAndCourseSectionIdAndAssessmentIdIsNull(
                        studentId, enrollment.getCourseSectionId());
                if (grade.isPresent() && grade.get().isPublished()) {
                    if (GradeResult.fromLetter(grade.get().getLetter()).isPass()) {
                        earnedCredits += credits;
                    }
                }
            }
        }
        if (attemptedCredits == 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(earnedCredits)
                .divide(BigDecimal.valueOf(attemptedCredits), 4, RoundingMode.HALF_UP);
    }

    private static boolean meetsSap(BigDecimal gpa, BigDecimal completionRate) {
        if (gpa == null || gpa.compareTo(MIN_GPA) < 0) {
            return false;
        }
        return completionRate.compareTo(MIN_COMPLETION_RATE) >= 0;
    }

    private static String formatGpa(BigDecimal gpa) {
        return gpa == null ? "n/a" : gpa.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatRate(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private void requireStudent(UUID studentId) {
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinancialAidErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
    }

    private void requireTerm(UUID academicTermId) {
        academicStructure
                .findTerm(academicTermId, Instant.now())
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.SAP_EVALUATION_NOT_FOUND,
                        "No academic term exists with id " + academicTermId));
    }
}
