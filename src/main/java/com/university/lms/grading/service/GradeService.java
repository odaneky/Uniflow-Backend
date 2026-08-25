package com.university.lms.grading.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.telemetry.UniFlowMetrics;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.assessment.repository.AssessmentRepository;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.grading.api.AcademicRecord;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.domain.GradeResult;
import com.university.lms.grading.domain.GradeRevision;
import com.university.lms.grading.domain.GradeScale;
import com.university.lms.grading.domain.GradeScaleBand;
import com.university.lms.grading.domain.GradingErrorCode;
import com.university.lms.grading.dto.AcademicSummaryResponse;
import com.university.lms.grading.dto.CreateGradeRequest;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.GradeRevisionRepository;
import com.university.lms.grading.repository.GradeScaleBandRepository;
import com.university.lms.grading.repository.GradeScaleRepository;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final GradeRevisionRepository gradeRevisionRepository;
    private final GradeScaleRepository gradeScaleRepository;
    private final GradeScaleBandRepository gradeScaleBandRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AcademicStructure academicStructure;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrail auditTrail;
    private final UniFlowMetrics metrics;
    private final GradeOutboxPublisher gradeOutboxPublisher;
    private final AssessmentRepository assessmentRepository;
    private final StaffAppointments staffAppointments;

    public GradeService(
            GradeRepository gradeRepository,
            GradeRevisionRepository gradeRevisionRepository,
            GradeScaleRepository gradeScaleRepository,
            GradeScaleBandRepository gradeScaleBandRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            AcademicStructure academicStructure,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            AuditTrail auditTrail,
            UniFlowMetrics metrics,
            GradeOutboxPublisher gradeOutboxPublisher,
            AssessmentRepository assessmentRepository,
            StaffAppointments staffAppointments) {
        this.gradeRepository = gradeRepository;
        this.gradeRevisionRepository = gradeRevisionRepository;
        this.gradeScaleRepository = gradeScaleRepository;
        this.gradeScaleBandRepository = gradeScaleBandRepository;
        this.academicStructure = academicStructure;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
        this.metrics = metrics;
        this.gradeOutboxPublisher = gradeOutboxPublisher;
        this.assessmentRepository = assessmentRepository;
        this.staffAppointments = staffAppointments;
    }

    public List<GradeResponse> gradebook(UUID sectionId) {
        requireTeacherOrAdmin(sectionId);
        CourseCatalog.SectionSummary section = courseCatalog.findSection(sectionId).orElse(null);
        CourseCatalog.CourseSummary course = section == null
                ? null
                : courseCatalog.findCourse(section.courseId()).orElse(null);
        Integer credits = course == null ? null : course.credits();
        Integer level = course == null ? null : course.level();
        String code = section == null ? null : section.courseCode();
        String title = section == null ? null : section.courseTitle();
        AcademicStructure.TermSummary term = section == null
                ? null
                : academicStructure.findTerm(section.academicTermId(), Instant.now()).orElse(null);
        return gradeRepository.findByCourseSectionId(sectionId).stream()
                .map(grade -> {
                    Integer attempt = enrollmentDirectory
                            .attemptNumberOf(grade.getStudentId(), grade.getCourseSectionId())
                            .orElse(null);
                    return GradeResponse.from(
                            grade,
                            code,
                            title,
                            credits,
                            term == null ? null : term.academicYearCode(),
                            term == null ? null : term.name(),
                            level,
                            attempt);
                })
                .toList();
    }

    /**
     * D8: no export endpoint existed anywhere in the system — a registrar wanting a section's
     * grades in a spreadsheet had no path but to transcribe the JSON gradebook by hand. Reuses
     * {@link #gradebook} for both the data and its authorization check, so this is exactly the
     * same rows a caller was already entitled to see, just spreadsheet-shaped.
     */
    public String exportGradebookCsv(UUID sectionId) {
        List<GradeResponse> rows = gradebook(sectionId);
        StringBuilder csv = new StringBuilder(
                "Student Number,Student Name,Course Code,Course Title,Letter,Percentage,Grade Point,Attempt,Recorded At\n");
        for (GradeResponse row : rows) {
            StudentDirectory.StudentSummary student =
                    studentDirectory.findById(row.studentId()).orElse(null);
            String studentNumber = student == null ? "" : student.studentNumber();
            String fullName = student == null
                    ? ""
                    : userDirectory.findById(student.userId()).map(UserDirectory.UserSummary::fullName).orElse("");
            csv.append(csvField(studentNumber)).append(',')
                    .append(csvField(fullName)).append(',')
                    .append(csvField(row.courseCode())).append(',')
                    .append(csvField(row.courseTitle())).append(',')
                    .append(csvField(row.letter())).append(',')
                    .append(csvField(row.percentage() == null ? "" : row.percentage().toPlainString())).append(',')
                    .append(csvField(row.gradePoint() == null ? "" : row.gradePoint().toPlainString())).append(',')
                    .append(csvField(row.attemptNumber() == null ? "" : row.attemptNumber().toString())).append(',')
                    .append(csvField(row.recordedAt() == null ? "" : row.recordedAt().toString()))
                    .append('\n');
        }
        return csv.toString();
    }

    /** RFC 4180: quote every field and double up any embedded quote. Simplest rule that is always correct. */
    private static String csvField(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
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

        // Most recent published overall per course counts for GPA; credits only if that sit is PASS.
        // "Most recent" is by term_order — the institutional chronological position snapshotted at
        // award — not created_at, which a late-entered correction would otherwise put out of order.
        Map<UUID, Grade> latestByCourse = new LinkedHashMap<>();
        List<Grade> chronological = overall.stream()
                .sorted(Comparator.comparingInt(Grade::getTermOrder))
                .toList();
        for (Grade grade : chronological) {
            latestByCourse.put(grade.getCourseId(), grade);
        }

        int creditsEarned = 0;
        BigDecimal weightedPoints = BigDecimal.ZERO;
        int weightedCredits = 0;
        for (Grade grade : latestByCourse.values()) {
            int credits = grade.getCredits();
            weightedPoints = weightedPoints.add(grade.getGradePoint().multiply(BigDecimal.valueOf(credits)));
            weightedCredits += credits;
            if (GradeResult.fromLetter(grade.getLetter()).isPass()) {
                creditsEarned += credits;
            }
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
                        grade.getCourseSectionId(),
                        grade.getLetter(),
                        grade.getGradePoint(),
                        GradeResult.fromLetter(grade.getLetter()).isPass(),
                        grade.getCreatedAt()))
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
        Grade grade = existing.orElseGet(() -> {
            CourseCatalog.SectionSummary section = courseCatalog
                    .findSection(request.courseSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            GradingErrorCode.GRADE_SECTION_NOT_FOUND,
                            "No course section exists with id " + request.courseSectionId()));
            int credits = courseCatalog
                    .findCourse(section.courseId())
                    .map(CourseCatalog.CourseSummary::credits)
                    .orElse(0);
            return new Grade(
                    request.studentId(),
                    request.courseSectionId(),
                    scale,
                    request.percentage(),
                    band.getLetter(),
                    band.getGradePoint(),
                    section.courseId(),
                    section.academicTermId(),
                    credits,
                    academicStructure.termOrdinal(section.academicTermId()));
        });

        BigDecimal beforePercentage = existing.map(Grade::getPercentage).orElse(null);
        String beforeLetter = existing.map(Grade::getLetter).orElse(null);
        BigDecimal beforeGradePoint = existing.map(Grade::getGradePoint).orElse(null);
        if (existing.isPresent() && (request.reason() == null || request.reason().isBlank())) {
            throw new ValidationException(
                    GradingErrorCode.GRADE_REVISION_REASON_REQUIRED,
                    "A reason is required to change a grade that was already awarded");
        }
        String reason = existing.isPresent() ? request.reason() : orDefault(request.reason(), "Initial award");

        revise(grade, request.percentage(), band.getLetter(), band.getGradePoint());
        if (request.assessmentId() != null) {
            grade.forAssessment(request.assessmentId());
        }
        if (Boolean.TRUE.equals(request.publish())) {
            grade.publish();
        }
        Grade saved = gradeRepository.save(grade);
        recordRevision(saved, beforePercentage, beforeLetter, beforeGradePoint, reason);
        if (saved.isPublished()) {
            String action = wasPublished ? AuditTrail.Action.GRADE_CHANGED : AuditTrail.Action.GRADE_PUBLISHED;
            recordGrade(action, saved);
            metrics.grade(wasPublished ? "changed" : "published");
            if (!wasPublished) {
                gradeOutboxPublisher.publishPublished(saved);
            }
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
            gradeOutboxPublisher.publishPublished(grade);
        }
        return GradeResponse.from(grade);
    }

    /** Translates the domain's lock guard into the API's error contract. */
    private void revise(Grade grade, BigDecimal percentage, String letter, BigDecimal gradePoint) {
        try {
            grade.revise(percentage, letter, gradePoint);
        } catch (IllegalStateException ex) {
            throw new BusinessException(GradingErrorCode.GRADE_LOCKED, ex.getMessage());
        }
    }

    private void recordRevision(
            Grade grade,
            BigDecimal beforePercentage,
            String beforeLetter,
            BigDecimal beforeGradePoint,
            String reason) {
        int revisionNumber = gradeRevisionRepository.countByGradeId(grade.getId()) + 1;
        UUID changedBy = currentUserProvider.require().userId();
        gradeRevisionRepository.save(new GradeRevision(
                grade,
                revisionNumber,
                beforePercentage,
                beforeLetter,
                beforeGradePoint,
                grade.getPercentage(),
                grade.getLetter(),
                grade.getGradePoint(),
                reason,
                changedBy,
                null));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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


    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GradingErrorCode.GRADE_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));
        if (isAuthorizedAdmin(caller, section)) {
            return;
        }
        if (caller.hasRole(SecurityRoles.LECTURER) && courseCatalog.teaches(caller.userId(), sectionId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to change this section");
    }

    /**
     * A5: SYSTEM_ADMIN/REGISTRAR/FACULTY_ADMIN previously bypassed section-department scoping
     * unconditionally here — grade entry is the highest-stakes instance of the {@code
     * requireTeacherOrAdmin} over-reach already fixed in {@code AssessmentService} and {@code
     * LearningService}. Deliberately a separate check from LECTURER's, which stays gated on {@code
     * courseCatalog.teaches}, unchanged: department appointment is not the same claim as actually
     * teaching this section. Same fail-open resolution and SYSTEM_ADMIN carve-out as the other A5
     * guards.
     */
    private boolean isAuthorizedAdmin(CurrentUser caller, CourseCatalog.SectionSummary section) {
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN))) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return true;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return true;
        }
        Optional<UUID> orgUnitId = courseCatalog
                .departmentOfCourse(section.courseId())
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        return orgUnitId.isEmpty() || staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get());
    }

    public Optional<BigDecimal> computeWeightedOverall(UUID studentId, UUID sectionId) {
        requireTeacherOrAdmin(sectionId);
        List<Grade> assessmentGrades = gradeRepository.findAllByStudentIdAndPublishedTrue(studentId).stream()
                .filter(grade -> grade.getCourseSectionId().equals(sectionId))
                .filter(grade -> grade.getAssessmentId() != null)
                .toList();
        if (assessmentGrades.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal weighted = BigDecimal.ZERO;
        for (Grade grade : assessmentGrades) {
            BigDecimal weight = assessmentRepository
                    .findById(grade.getAssessmentId())
                    .map(a -> a.getWeightPercent())
                    .orElse(BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            weighted = weighted.add(grade.getPercentage().multiply(weight));
        }
        return Optional.of(weighted.setScale(2, RoundingMode.HALF_UP));
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
