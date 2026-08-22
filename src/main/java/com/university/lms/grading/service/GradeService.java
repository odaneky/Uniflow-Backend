package com.university.lms.grading.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.telemetry.UniFlowMetrics;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.domain.GradeScale;
import com.university.lms.grading.domain.GradeScaleBand;
import com.university.lms.grading.domain.GradingErrorCode;
import com.university.lms.grading.dto.AcademicSummaryResponse;
import com.university.lms.grading.dto.CreateGradeRequest;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.GradeScaleBandRepository;
import com.university.lms.grading.repository.GradeScaleRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Awarding and reading grades.
 *
 * <p>Letter and grade-point come from the scale that owns the percentage, never from the request.
 * Students see published rows only; the filter is applied in the query.
 */
@Service
@Transactional(readOnly = true)
public class GradeService implements AcademicRecord {

    static final String DEFAULT_SCALE_NAME = "Undergraduate Standard";

    private final GradeRepository gradeRepository;
    private final GradeScaleRepository gradeScaleRepository;
    private final GradeScaleBandRepository gradeScaleBandRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrail auditTrail;
    private final UniFlowMetrics metrics;

    public GradeService(
            GradeRepository gradeRepository,
            GradeScaleRepository gradeScaleRepository,
            GradeScaleBandRepository gradeScaleBandRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            CurrentUserProvider currentUserProvider,
            AuditTrail auditTrail,
            UniFlowMetrics metrics) {
        this.gradeRepository = gradeRepository;
        this.gradeScaleRepository = gradeScaleRepository;
        this.gradeScaleBandRepository = gradeScaleBandRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
        this.metrics = metrics;
    }

    public List<GradeResponse> gradebook(UUID sectionId) {
        requireTeacherOrAdmin(sectionId);
        return gradeRepository.findByCourseSectionId(sectionId).stream()
                .map(GradeResponse::from)
                .toList();
    }

    public AcademicSummaryResponse ownSummary() {
        CurrentUser caller = currentUserProvider.require();
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        Summary summary = summaryOf(studentId);
        return new AcademicSummaryResponse(
                summary.gpa(), summary.creditsAttempted(), summary.creditsEarned(), summary.publishedGradeCount());
    }

    @Override
    public Summary summaryOf(UUID studentId) {
        List<Grade> published = gradeRepository.findAllByStudentIdAndPublishedTrue(studentId);
        List<Grade> overall = published.stream().filter(grade -> grade.getAssessmentId() == null).toList();

        int creditsEarned = 0;
        BigDecimal weightedPoints = BigDecimal.ZERO;
        int weightedCredits = 0;
        for (Grade grade : overall) {
            int credits = courseCatalog
                    .findSection(grade.getCourseSectionId())
                    .flatMap(section -> courseCatalog.findCourse(section.courseId()))
                    .map(CourseCatalog.CourseSummary::credits)
                    .orElse(0);
            creditsEarned += credits;
            weightedPoints = weightedPoints.add(grade.getGradePoint().multiply(BigDecimal.valueOf(credits)));
            weightedCredits += credits;
        }

        int creditsAttempted = enrollmentDirectory.accessibleSectionIds(studentId).stream()
                .map(courseCatalog::findSection)
                .flatMap(Optional::stream)
                .map(section -> courseCatalog.findCourse(section.courseId()))
                .flatMap(Optional::stream)
                .mapToInt(CourseCatalog.CourseSummary::credits)
                .sum();

        BigDecimal gpa = weightedCredits == 0
                ? null
                : weightedPoints.divide(BigDecimal.valueOf(weightedCredits), 2, RoundingMode.HALF_UP);

        return new Summary(gpa, creditsAttempted, creditsEarned, published.size());
    }

    @Override
    public List<PublishedOverall> publishedOverallOf(UUID studentId) {
        return gradeRepository.findAllByStudentIdAndPublishedTrue(studentId).stream()
                .filter(grade -> grade.getAssessmentId() == null)
                .map(grade -> new PublishedOverall(
                        grade.getCourseSectionId(), grade.getLetter(), grade.getGradePoint()))
                .toList();
    }

    @Transactional
    public GradeResponse award(CreateGradeRequest request) {
        requireTeacherOrAdmin(request.courseSectionId());
        if (!studentDirectory.exists(request.studentId())) {
            throw new ResourceNotFoundException(
                    GradingErrorCode.GRADE_STUDENT_NOT_FOUND, "No student exists with id " + request.studentId());
        }

        GradeScale scale = resolveScale(request.gradeScaleId());
        GradeScaleBand band = bandFor(scale.getId(), request.percentage());

        Optional<Grade> existing =
                findExisting(request.studentId(), request.courseSectionId(), request.assessmentId());
        boolean wasPublished = existing.map(Grade::isPublished).orElse(false);
        Grade grade = existing.orElseGet(() -> new Grade(
                request.studentId(),
                request.courseSectionId(),
                scale,
                request.percentage(),
                band.getLetter(),
                band.getGradePoint()));
        grade.revise(request.percentage(), band.getLetter(), band.getGradePoint());
        if (request.assessmentId() != null) {
            grade.forAssessment(request.assessmentId());
        }
        if (Boolean.TRUE.equals(request.publish())) {
            grade.publish();
        }
        Grade saved = gradeRepository.save(grade);
        if (saved.isPublished()) {
            String action = wasPublished ? AuditTrail.Action.GRADE_CHANGED : AuditTrail.Action.GRADE_PUBLISHED;
            recordGrade(action, saved);
            metrics.grade(wasPublished ? "changed" : "published");
        }
        return GradeResponse.from(saved);
    }

    @Transactional
    public GradeResponse publish(UUID gradeId) {
        Grade grade = gradeRepository
                .findById(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GradingErrorCode.GRADE_NOT_FOUND, "No grade exists with id " + gradeId));
        requireTeacherOrAdmin(grade.getCourseSectionId());
        boolean wasPublished = grade.isPublished();
        grade.publish();
        if (!wasPublished) {
            recordGrade(AuditTrail.Action.GRADE_PUBLISHED, grade);
            metrics.grade("published");
        }
        return GradeResponse.from(grade);
    }

    private Optional<Grade> findExisting(UUID studentId, UUID sectionId, UUID assessmentId) {
        if (assessmentId == null) {
            return gradeRepository.findByStudentIdAndCourseSectionIdAndAssessmentIdIsNull(studentId, sectionId);
        }
        return gradeRepository.findByStudentIdAndCourseSectionIdAndAssessmentId(studentId, sectionId, assessmentId);
    }

    private GradeScale resolveScale(UUID gradeScaleId) {
        if (gradeScaleId != null) {
            return gradeScaleRepository
                    .findById(gradeScaleId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            GradingErrorCode.GRADE_SCALE_NOT_FOUND, "No grade scale exists with id " + gradeScaleId));
        }
        return gradeScaleRepository
                .findByName(DEFAULT_SCALE_NAME)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GradingErrorCode.GRADE_SCALE_NOT_FOUND, "Default grade scale is not installed"));
    }

    GradeScaleBand bandFor(UUID scaleId, BigDecimal percentage) {
        return gradeScaleBandRepository.findByGradeScaleId(scaleId).stream()
                .filter(band -> band.contains(percentage))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        GradingErrorCode.GRADE_BAND_NOT_FOUND,
                        "No grade band covers percentage " + percentage));
    }

    private void requireKnownSection(UUID sectionId) {
        courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GradingErrorCode.GRADE_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));
    }

    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)) {
            requireKnownSection(sectionId);
            return;
        }
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GradingErrorCode.GRADE_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));
        if (caller.hasRole(SecurityRoles.LECTURER) && caller.userId().equals(section.lecturerUserId())) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to change this section");
    }

    private void recordGrade(String action, Grade grade) {
        CurrentUser actor = currentUserProvider.find().orElse(null);
        String sectionLabel = courseCatalog
                .findSection(grade.getCourseSectionId())
                .map(section -> section.courseCode() + " " + section.sectionCode())
                .orElse(grade.getCourseSectionId().toString());
        String studentNo = studentDirectory
                .findById(grade.getStudentId())
                .map(StudentDirectory.StudentSummary::studentNumber)
                .orElse(grade.getStudentId().toString());
        auditTrail.record(
                actor == null ? null : actor.userId(),
                actor == null ? null : actorLabel(actor),
                action,
                AuditTrail.EntityType.GRADE,
                grade.getId(),
                studentNo + " · " + sectionLabel + " · " + grade.getLetter());
    }

    private static String actorLabel(CurrentUser actor) {
        if (actor.fullName() != null && !actor.fullName().isBlank()) {
            return actor.fullName();
        }
        return actor.username();
    }

}
