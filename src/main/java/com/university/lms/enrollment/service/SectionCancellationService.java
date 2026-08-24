package com.university.lms.enrollment.service;

import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.api.SectionActions;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentErrorCode;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.dto.SectionCancellationResponse;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelling a section is not just a status flip on the section: every student holding a seat or a
 * waitlist spot must be released, charged students must be credited back, and everyone affected
 * must be told. {@code CourseService.cancelSection} used to be the entire operation — the section
 * flipped to CANCELLED and nothing else happened, silently leaving students enrolled (and billed)
 * in a course that no longer runs.
 *
 * <p>Lives in {@code enrollment}, not {@code course}: this needs {@code EnrollmentRepository} and
 * {@code StudentBilling}, and {@code enrollment} already depends on {@code course} for {@link
 * CourseCatalog} — the other direction would be the module graph's first cycle. {@link
 * SectionActions} is the one write {@code course} exposes back so the section flip itself still
 * happens through {@code CourseService}, not duplicated here.
 *
 * <p>Does not attempt a minimum-credit-load recheck: flagging a student who dropped below their
 * programme's minimum load is an advising concern with no workflow to route it to yet, not
 * something to silently paper over here.
 */
@Service
@Transactional(readOnly = true)
public class SectionCancellationService {

    private static final Logger log = LoggerFactory.getLogger(SectionCancellationService.class);

    private final SectionActions sectionActions;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseCatalog courseCatalog;
    private final StudentBilling studentBilling;
    private final AuditTrail auditTrail;
    private final EnrollmentOutboxPublisher outboxPublisher;
    private final CurrentUserProvider currentUserProvider;

    public SectionCancellationService(
            SectionActions sectionActions,
            EnrollmentRepository enrollmentRepository,
            CourseCatalog courseCatalog,
            StudentBilling studentBilling,
            AuditTrail auditTrail,
            EnrollmentOutboxPublisher outboxPublisher,
            CurrentUserProvider currentUserProvider) {
        this.sectionActions = sectionActions;
        this.enrollmentRepository = enrollmentRepository;
        this.courseCatalog = courseCatalog;
        this.studentBilling = studentBilling;
        this.auditTrail = auditTrail;
        this.outboxPublisher = outboxPublisher;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public SectionCancellationResponse cancel(UUID sectionId) {
        requireRegistry();
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EnrollmentErrorCode.ENROLLMENT_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));

        sectionActions.cancel(sectionId);

        List<Enrollment> affected = enrollmentRepository.findByCourseSectionIdAndStatusIn(
                sectionId, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED));

        int seatsReleased = 0;
        for (Enrollment enrolment : affected) {
            boolean wasHoldingSeat = enrolment.occupiesSeat();
            endForCancellation(enrolment);
            if (wasHoldingSeat) {
                courseCatalog.releaseSeat(sectionId);
                studentBilling.creditForDrop(
                        enrolment.getStudentId(),
                        enrolment.getId(),
                        section.academicTermId(),
                        section.courseCode(),
                        !hasOtherEnrolmentInTerm(enrolment.getStudentId(), section.academicTermId(), sectionId));
                seatsReleased++;
            }
            recordAudit(enrolment, section);
            outboxPublisher.publishSectionCancelled(enrolment);
        }
        log.info("Cancelled section {} ({} students affected, {} seats released)", sectionId, affected.size(), seatsReleased);
        return new SectionCancellationResponse(sectionId, affected.size(), seatsReleased);
    }

    private void endForCancellation(Enrollment enrolment) {
        try {
            enrolment.transitionTo(EnrollmentStatus.DROPPED);
        } catch (IllegalStateException ex) {
            throw new BusinessException(EnrollmentErrorCode.INVALID_ENROLLMENT_STATE, ex.getMessage());
        }
    }

    /** Excludes the section being cancelled — it no longer counts toward "another" enrolment in the term. */
    private boolean hasOtherEnrolmentInTerm(UUID studentId, UUID termId, UUID excludingSectionId) {
        return enrollmentRepository.findByStudentIdAndStatusIn(studentId, List.of(EnrollmentStatus.ENROLLED)).stream()
                .filter(row -> !row.getCourseSectionId().equals(excludingSectionId))
                .anyMatch(row -> courseCatalog
                        .findSection(row.getCourseSectionId())
                        .map(s -> s.academicTermId().equals(termId))
                        .orElse(false));
    }

    private void recordAudit(Enrollment enrolment, CourseCatalog.SectionSummary section) {
        CurrentUser actor = currentUserProvider.find().orElse(null);
        String details = section.courseCode() + " " + section.sectionCode() + " was cancelled";
        auditTrail.record(
                actor == null ? null : actor.userId(),
                actor == null ? null : actorLabel(actor),
                AuditTrail.Action.ENROLMENT_CANCELLED_BY_INSTITUTION,
                AuditTrail.EntityType.ENROLLMENT,
                enrolment.getId(),
                details);
    }

    private static String actorLabel(CurrentUser actor) {
        if (actor.fullName() != null && !actor.fullName().isBlank()) {
            return actor.fullName();
        }
        return actor.username();
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(CommonErrorCode.ACCESS_DENIED, "You do not have permission to cancel a section");
        }
    }
}
