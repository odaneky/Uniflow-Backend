package com.university.lms.enrollment.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.university.lms.enrollment.dto.MyCourseResponse;
import com.university.lms.enrollment.dto.RegistrationContextResponse;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service reads over the caller's own enrolments.
 *
 * <p>Separate from {@link EnrollmentService} because the question is different: that service answers
 * "act on this enrolment, if you may", this one answers "what is mine". Keeping them apart means the
 * self-service path has no parameter through which another student could be named at all, rather
 * than relying on a check to reject one.
 */
@Service
@Transactional(readOnly = true)
public class MyEnrolmentService {

    /**
     * A registration load is tens of rows, not thousands, so one page is the whole set. Bounded
     * anyway — an unbounded query is how a self-service endpoint becomes a denial-of-service lever.
     */
    private static final int MAX_COURSES = 200;

    private final EnrollmentRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final StudentDirectory studentDirectory;
    private final CourseCatalog courseCatalog;
    private final AcademicStructure academicStructure;
    private final StudentBilling studentBilling;

    public MyEnrolmentService(
            EnrollmentRepository repository,
            CurrentUserProvider currentUserProvider,
            StudentDirectory studentDirectory,
            CourseCatalog courseCatalog,
            AcademicStructure academicStructure,
            StudentBilling studentBilling) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.studentDirectory = studentDirectory;
        this.courseCatalog = courseCatalog;
        this.academicStructure = academicStructure;
        this.studentBilling = studentBilling;
    }

    public PageResponse<EnrollmentResponse> ownEnrolments(Pageable pageable) {
        return PageResponse.from(
                repository.search(ownStudentId(), null, null, pageable), EnrollmentResponse::from);
    }

    public List<MyCourseResponse> ownCourses(UUID academicTermId) {
        return repository
                .search(ownStudentId(), null, null, PageRequest.of(0, MAX_COURSES))
                .stream()
                .filter(enrolment -> enrolment.getStatus().occupiesTimetable())
                .map(enrolment -> courseCatalog
                        .findSection(enrolment.getCourseSectionId())
                        .map(section -> {
                            var course = courseCatalog.findCourse(section.courseId());
                            return new MyCourseResponse(
                                    enrolment.getId(),
                                    section.id(),
                                    section.courseId(),
                                    section.courseCode(),
                                    course.map(CourseCatalog.CourseSummary::title).orElse(section.courseCode()),
                                    course.map(CourseCatalog.CourseSummary::credits).orElse(0),
                                    section.sectionCode(),
                                    section.academicTermId(),
                                    enrolment.getStatus(),
                                    enrolment.getEnrolledAt(),
                                    courseCatalog.meetingsOf(section.id()));
                        })
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(course -> academicTermId == null || academicTermId.equals(course.academicTermId()))
                .toList();
    }

    public RegistrationContextResponse ownRegistrationContext() {
        UUID studentId = ownStudentId();
        UUID programmeId = studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::programmeId).orElse(null);
        var load = academicStructure.creditLoadFor(programmeId);
        var quote = studentBilling.quoteFor(studentId);
        Instant now = Instant.now();
        var calendar = academicStructure.currentTerm(now);
        if (calendar.isEmpty()) {
            return RegistrationContextResponse.closed(load, quote, 0);
        }
        AcademicStructure.TermCalendar term = calendar.get();
        int credits = ownCourses(term.id()).stream()
                .filter(course -> course.status() == EnrollmentStatus.ENROLLED)
                .mapToInt(MyCourseResponse::credits)
                .sum();
        boolean canAdd = academicStructure.canAddEnrolment(term.id(), now);
        boolean canDrop = academicStructure.canDropWithoutPenalty(term.id(), now);
        boolean canWithdraw = "WITHDRAW_ONLY".equals(term.phase());
        int hours = academicStructure.checkoutCorrectionHours();
        var correction = latestOpenCheckout(studentId, hours, canDrop, now);
        return new RegistrationContextResponse(
                term.id(),
                term.name(),
                term.startDate(),
                term.endDate(),
                term.registrationOpensAt(),
                term.registrationClosesAt(),
                term.addDropOpensAt(),
                term.addDropClosesAt(),
                term.tuitionDueOn(),
                term.phase(),
                canAdd,
                canDrop,
                canWithdraw,
                correction.batchId(),
                correction.expiresAt(),
                hours,
                correction.allowed(),
                credits,
                load.minSemesterCredits(),
                load.maxSemesterCredits(),
                quote.amountPerCredit(),
                quote.campusFee(),
                quote.catalogFees());
    }

    private record CheckoutCorrection(UUID batchId, Instant expiresAt, boolean allowed) {}

    private CheckoutCorrection latestOpenCheckout(UUID studentId, int hours, boolean windowOpen, Instant now) {
        List<Enrollment> open = repository.findByStudentIdAndStatusIn(
                        studentId,
                        List.of(EnrollmentStatus.PENDING, EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED))
                .stream()
                .filter(row -> row.getCheckoutBatchId() != null)
                .toList();
        if (open.isEmpty()) {
            return new CheckoutCorrection(null, null, false);
        }
        Instant latest = open.stream().map(Enrollment::getEnrolledAt).max(Instant::compareTo).orElse(now);
        UUID batchId = open.stream()
                .filter(row -> row.getEnrolledAt().equals(latest))
                .map(Enrollment::getCheckoutBatchId)
                .findFirst()
                .orElse(null);
        if (batchId == null) {
            return new CheckoutCorrection(null, null, false);
        }
        Instant started = open.stream()
                .filter(row -> batchId.equals(row.getCheckoutBatchId()))
                .map(Enrollment::getEnrolledAt)
                .min(Instant::compareTo)
                .orElse(latest);
        Instant expires = hours <= 0 ? null : started.plus(Duration.ofHours(hours));
        boolean allowed = windowOpen && expires != null && !now.isAfter(expires);
        return new CheckoutCorrection(batchId, expires, allowed);
    }

    /** A caller with no student record owns no enrolments, and is refused rather than shown none. */
    private UUID ownStudentId() {
        return Optional.of(currentUserProvider.require())
                .flatMap(caller -> studentDirectory.studentIdOfUser(caller.userId()))
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
    }
}
