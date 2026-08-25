package com.university.lms.grading.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.grading.domain.AcademicStanding;
import com.university.lms.grading.domain.AcademicStandingEvent;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.domain.GradeResult;
import com.university.lms.grading.domain.TermAcademicRecord;
import com.university.lms.grading.dto.TermCloseResponse;
import com.university.lms.grading.repository.AcademicStandingEventRepository;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.TermAcademicRecordRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.AcademicStandingOutcome;
import com.university.lms.student.api.StudentLifecycle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes a term: locks its grades so they stop accepting a direct revision (see {@link
 * Grade#lock}), and writes one {@link TermAcademicRecord} per student who has a published overall
 * result that term.
 *
 * <p>Idempotent and safe to re-run: a grade already locked is left alone, and a student who
 * already has a record for this term is skipped rather than overwritten — {@code
 * TermAcademicRecord} has no update path by design (see its javadoc). The whole operation runs in
 * one transaction, so a failure partway through leaves nothing partially committed to resume from
 * — re-running from the top is always correct.
 *
 * <p>Deliberately does not yet re-run SAP or recalculate holds, despite the plan calling for both
 * at term close: financial aid's SAP and holds logic is a separate, already-substantial subsystem
 * this pass has not reviewed, and wiring it in without that review risks a real regression in aid
 * eligibility. Left as explicitly out of scope for a dedicated follow-up.
 */
@Service
@Transactional(readOnly = true)
public class TermCloseService {

    private static final BigDecimal GOOD_STANDING_FLOOR = new BigDecimal("2.00");

    private final GradeRepository gradeRepository;
    private final TermAcademicRecordRepository termAcademicRecordRepository;
    private final AcademicStandingEventRepository academicStandingEventRepository;
    private final AcademicStructure academicStructure;
    private final GradeService gradeService;
    private final StudentLifecycle studentLifecycle;
    private final CurrentUserProvider currentUserProvider;

    public TermCloseService(
            GradeRepository gradeRepository,
            TermAcademicRecordRepository termAcademicRecordRepository,
            AcademicStandingEventRepository academicStandingEventRepository,
            AcademicStructure academicStructure,
            GradeService gradeService,
            StudentLifecycle studentLifecycle,
            CurrentUserProvider currentUserProvider) {
        this.gradeRepository = gradeRepository;
        this.termAcademicRecordRepository = termAcademicRecordRepository;
        this.academicStandingEventRepository = academicStandingEventRepository;
        this.academicStructure = academicStructure;
        this.gradeService = gradeService;
        this.studentLifecycle = studentLifecycle;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public TermCloseResponse closeTerm(UUID academicTermId) {
        requireRegistry();
        CurrentUser actor = currentUserProvider.require();
        int termOrder = academicStructure.termOrdinal(academicTermId);

        List<Grade> termGrades = gradeRepository.findByAcademicTermIdAndAssessmentIdIsNullAndPublishedTrue(academicTermId);
        int locked = 0;
        for (Grade grade : termGrades) {
            if (!grade.isLocked()) {
                grade.lock(actor.userId());
                locked++;
            }
        }

        Map<UUID, List<Grade>> byStudent = new LinkedHashMap<>();
        for (Grade grade : termGrades) {
            byStudent.computeIfAbsent(grade.getStudentId(), id -> new java.util.ArrayList<>()).add(grade);
        }

        int recorded = 0;
        int skipped = 0;
        for (Map.Entry<UUID, List<Grade>> entry : byStudent.entrySet()) {
            UUID studentId = entry.getKey();
            if (termAcademicRecordRepository.existsByStudentIdAndAcademicTermId(studentId, academicTermId)) {
                skipped++;
                continue;
            }
            recordFor(studentId, academicTermId, termOrder, entry.getValue());
            recorded++;
        }

        return new TermCloseResponse(academicTermId, termGrades.size(), locked, byStudent.size(), recorded, skipped);
    }

    private void recordFor(UUID studentId, UUID academicTermId, int termOrder, List<Grade> studentTermGrades) {
        int creditsAttempted = 0;
        int creditsEarned = 0;
        BigDecimal weightedPoints = BigDecimal.ZERO;
        for (Grade grade : studentTermGrades) {
            int credits = grade.getCredits();
            creditsAttempted += credits;
            weightedPoints = weightedPoints.add(grade.getGradePoint().multiply(BigDecimal.valueOf(credits)));
            if (GradeResult.fromLetter(grade.getLetter()).isPass()) {
                creditsEarned += credits;
            }
        }
        BigDecimal termGpa = creditsAttempted == 0
                ? null
                : weightedPoints.divide(BigDecimal.valueOf(creditsAttempted), 2, RoundingMode.HALF_UP);

        var cumulative = gradeService.summaryOf(studentId);
        AcademicStanding standing = cumulative.gpa() != null && cumulative.gpa().compareTo(GOOD_STANDING_FLOOR) < 0
                ? AcademicStanding.PROBATION
                : AcademicStanding.GOOD_STANDING;

        termAcademicRecordRepository.save(new TermAcademicRecord(
                studentId,
                academicTermId,
                termOrder,
                termGpa,
                cumulative.gpa(),
                creditsAttempted,
                creditsEarned,
                cumulative.creditsEarned(),
                standing));

        recordStanding(studentId, academicTermId, termOrder, standing, cumulative.gpa());
    }

    /**
     * Writes the term's standing decision and, for the two outcomes that are safe to drive
     * automatically, applies it to the student's status. Kept as its own step from {@link
     * TermAcademicRecord}: that row is the computed answer, this is the decision log, and only the
     * second one ever drives a status change.
     */
    private void recordStanding(
            UUID studentId, UUID academicTermId, int termOrder, AcademicStanding standing, BigDecimal cumulativeGpa) {
        AcademicStanding previous = academicStandingEventRepository
                .findTopByStudentIdOrderByTermOrderDesc(studentId)
                .map(AcademicStandingEvent::getToStanding)
                .orElse(null);
        String reason = standing == AcademicStanding.PROBATION
                ? "Cumulative GPA " + cumulativeGpa + " is below the " + GOOD_STANDING_FLOOR + " good-standing floor"
                : "Cumulative GPA " + (cumulativeGpa == null ? "n/a" : cumulativeGpa) + " meets the " + GOOD_STANDING_FLOOR
                        + " good-standing floor";
        academicStandingEventRepository.save(new AcademicStandingEvent(
                studentId, academicTermId, termOrder, previous, standing, reason, null, LocalDate.now(), null));

        if (previous != standing) {
            AcademicStandingOutcome outcome =
                    standing == AcademicStanding.PROBATION ? AcademicStandingOutcome.PROBATION : AcademicStandingOutcome.ACTIVE;
            studentLifecycle.applyAcademicStanding(studentId, outcome, reason);
        }
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You do not have permission to close a term");
        }
    }
}
