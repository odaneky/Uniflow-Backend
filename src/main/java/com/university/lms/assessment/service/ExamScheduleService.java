package com.university.lms.assessment.service;

import com.university.lms.assessment.domain.AssessmentErrorCode;
import com.university.lms.assessment.domain.ExamInvigilator;
import com.university.lms.assessment.domain.ExamMisconductRecord;
import com.university.lms.assessment.domain.ExamResitCandidate;
import com.university.lms.assessment.domain.ExamSitting;
import com.university.lms.assessment.dto.ExamInvigilatorResponse;
import com.university.lms.assessment.dto.ExamMisconductRecordResponse;
import com.university.lms.assessment.dto.ExamResitCandidateResponse;
import com.university.lms.assessment.dto.ExamSittingResponse;
import com.university.lms.assessment.dto.ReportExamMisconductRequest;
import com.university.lms.assessment.dto.ScheduleExamRequest;
import com.university.lms.assessment.repository.ExamInvigilatorRepository;
import com.university.lms.assessment.repository.ExamMisconductRecordRepository;
import com.university.lms.assessment.repository.ExamResitCandidateRepository;
import com.university.lms.assessment.repository.ExamSittingRepository;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.api.Notifier;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.student.api.StudentDirectory;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.domain.CourseErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduling exams, for the examinations office.
 *
 * <p>Sittings are created unpublished. A draft timetable is worked on for weeks and is wrong for
 * most of that time; releasing it is a separate, deliberate act.
 *
 * <p>Room double-booking is refused here as it is for teaching sessions — the same hall cannot hold
 * two exams at once, and discovering that on the morning is not recoverable.
 */
@Service
public class ExamScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ExamScheduleService.class);

    private static final String ENTITY = "ExamSitting";

    private final ExamSittingRepository examSittingRepository;
    private final ExamMisconductRecordRepository examMisconductRecordRepository;
    private final ExamInvigilatorRepository examInvigilatorRepository;
    private final ExamResitCandidateRepository examResitCandidateRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final Notifier notifier;
    private final AuditTrail auditTrail;
    private final CurrentUserProvider currentUserProvider;

    public ExamScheduleService(
            ExamSittingRepository examSittingRepository,
            ExamMisconductRecordRepository examMisconductRecordRepository,
            ExamInvigilatorRepository examInvigilatorRepository,
            ExamResitCandidateRepository examResitCandidateRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            Notifier notifier,
            AuditTrail auditTrail,
            CurrentUserProvider currentUserProvider) {
        this.examSittingRepository = examSittingRepository;
        this.examMisconductRecordRepository = examMisconductRecordRepository;
        this.examInvigilatorRepository = examInvigilatorRepository;
        this.examResitCandidateRepository = examResitCandidateRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.notifier = notifier;
        this.auditTrail = auditTrail;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public ExamSittingResponse schedule(UUID sectionId, ScheduleExamRequest request) {
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CourseErrorCode.COURSE_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));

        ExamSitting sitting = new ExamSitting(
                sectionId,
                request.title().trim(),
                request.startsAt(),
                request.durationMinutes(),
                request.room().trim(),
                request.seating() == null || request.seating().isBlank() ? null : request.seating().trim());
        if (request.assessmentId() != null) {
            sitting.linkAssessment(request.assessmentId());
        }

        requireRoomFree(sitting, null);

        ExamSitting saved = examSittingRepository.saveAndFlush(sitting);
        log.info("Scheduled exam {} for section {} at {}", saved.getId(), sectionId, saved.getStartsAt());
        return toResponse(saved, section);
    }

    /**
     * Moves a sitting.
     *
     * <p>If it was already published, the students sitting it are told. That is the whole point of
     * the operation being distinct from a create: an exam nobody knew about can be moved freely,
     * while one people have planned around cannot be moved quietly.
     */
    @Transactional
    public ExamSittingResponse reschedule(UUID sittingId, ScheduleExamRequest request) {
        ExamSitting sitting = require(sittingId);
        if (sitting.isCancelled()) {
            throw new BusinessException(
                    CourseErrorCode.INVALID_SECTION_STATE, "A cancelled sitting cannot be rescheduled.");
        }

        String previous = describe(sitting);
        sitting.reschedule(
                request.startsAt(),
                request.durationMinutes(),
                request.room().trim(),
                request.seating() == null || request.seating().isBlank() ? null : request.seating().trim());
        // Checked after the change and ignoring itself, so a sitting can be moved within its own hall.
        requireRoomFree(sitting, sitting.getId());

        auditTrail.record(
                actorId(),
                AuditTrail.Action.EXAM_RESCHEDULED,
                ENTITY,
                sittingId,
                "Moved from " + previous + " to " + describe(sitting));

        if (sitting.isPublished()) {
            notifyCandidates(
                    sitting,
                    "Exam moved: " + sitting.getTitle(),
                    "Your exam has been moved to " + describe(sitting) + ". It was " + previous + ".");
        }
        log.info("Rescheduled exam sitting {} from {} to {}", sittingId, previous, describe(sitting));
        return toResponse(sitting, courseCatalog.findSection(sitting.getCourseSectionId()).orElse(null));
    }

    /**
     * Withdraws a published sitting back to draft.
     *
     * <p>No notification: this is the office taking the timetable back to correct it, and telling
     * students an exam has vanished — before telling them where it went — causes more alarm than it
     * resolves. The reschedule that follows is what they hear about.
     */
    @Transactional
    public ExamSittingResponse unpublish(UUID sittingId) {
        ExamSitting sitting = require(sittingId);
        sitting.unpublish();
        auditTrail.record(actorId(), AuditTrail.Action.EXAM_UNPUBLISHED, ENTITY, sittingId, "Withdrawn to draft");
        log.info("Unpublished exam sitting {}", sittingId);
        return toResponse(sitting, courseCatalog.findSection(sitting.getCourseSectionId()).orElse(null));
    }

    /**
     * Cancels a sitting without deleting it.
     *
     * <p>Students are told if they could see it. A paper disappearing from a timetable with no
     * explanation is exactly the situation that fills the examinations office's inbox.
     */
    @Transactional
    public ExamSittingResponse cancel(UUID sittingId, String reason) {
        ExamSitting sitting = require(sittingId);
        boolean wasVisible = sitting.isPublished();
        String was = describe(sitting);
        sitting.cancel(reason == null || reason.isBlank() ? null : reason.trim());

        auditTrail.record(
                actorId(),
                AuditTrail.Action.EXAM_CANCELLED,
                ENTITY,
                sittingId,
                "Cancelled (" + was + ")" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()));

        if (wasVisible) {
            notifyCandidates(
                    sitting,
                    "Exam cancelled: " + sitting.getTitle(),
                    "Your exam scheduled for " + was + " has been cancelled."
                            + (reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim()));
        }
        log.info("Cancelled exam sitting {}", sittingId);
        return toResponse(sitting, courseCatalog.findSection(sitting.getCourseSectionId()).orElse(null));
    }

    /**
     * Tells everyone sitting this paper.
     *
     * <p>Best-effort per student: one unreachable recipient must not stop the rest being told, and
     * must not undo the change that prompted the message.
     */
    private void notifyCandidates(ExamSitting sitting, String title, String body) {
        for (UUID studentId : candidateStudentIds(sitting)) {
            studentDirectory
                    .userIdOfStudent(studentId)
                    .ifPresent(userId -> notifier.notifyUser(
                            userId, NotificationType.SYSTEM, title, body, "schedule"));
        }
    }

    /**
     * G6: the whole section roster, unless this sitting is a resit or deferred paper — signalled by
     * having any {@link ExamResitCandidate} rows at all — in which case only those named students.
     */
    private List<UUID> candidateStudentIds(ExamSitting sitting) {
        List<ExamResitCandidate> resitCandidates =
                examResitCandidateRepository.findByExamSittingIdOrderByAddedAtAsc(sitting.getId());
        if (!resitCandidates.isEmpty()) {
            return resitCandidates.stream().map(ExamResitCandidate::getStudentId).toList();
        }
        return enrollmentDirectory.rosterOf(sitting.getCourseSectionId()).stream()
                .map(EnrollmentDirectory.SectionEnrolment::studentId)
                .toList();
    }

    private static String describe(ExamSitting sitting) {
        return "%s in %s".formatted(sitting.getStartsAt(), sitting.getRoom());
    }

    private UUID actorId() {
        return currentUserProvider.find().map(CurrentUser::userId).orElse(null);
    }

    /** Releases a sitting to students. Until this happens they see nothing. */
    @Transactional
    public ExamSittingResponse publish(UUID sittingId) {
        ExamSitting sitting = require(sittingId);
        sitting.publish();
        log.info("Published exam sitting {}", sittingId);
        return toResponse(sitting, courseCatalog.findSection(sitting.getCourseSectionId()).orElse(null));
    }

    /**
     * Files a conduct report against a candidate in this sitting.
     *
     * <p>Append-only, deliberately: this is the examinations office's record of what was observed,
     * not a case management workflow. Disciplinary follow-up (G7) reads it rather than owns it.
     */
    @Transactional
    public ExamMisconductRecordResponse reportMisconduct(UUID sittingId, ReportExamMisconductRequest request) {
        require(sittingId);
        if (!studentDirectory.exists(request.studentId())) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.MISCONDUCT_STUDENT_NOT_FOUND,
                    "No student exists with id " + request.studentId());
        }

        UUID reporter = actorId();
        ExamMisconductRecord saved = examMisconductRecordRepository.save(
                new ExamMisconductRecord(sittingId, request.studentId(), request.description().trim(), reporter));

        auditTrail.record(
                reporter,
                AuditTrail.Action.EXAM_MISCONDUCT_REPORTED,
                ENTITY,
                sittingId,
                "Reported candidate " + request.studentId());
        log.info("Recorded misconduct report for student {} in sitting {}", request.studentId(), sittingId);
        return ExamMisconductRecordResponse.from(saved, nameOf(reporter));
    }

    @Transactional(readOnly = true)
    public List<ExamMisconductRecordResponse> misconductFor(UUID sittingId) {
        require(sittingId);
        List<ExamMisconductRecordResponse> responses = new ArrayList<>();
        for (ExamMisconductRecord record :
                examMisconductRecordRepository.findByExamSittingIdOrderByCreatedAtDesc(sittingId)) {
            responses.add(ExamMisconductRecordResponse.from(record, nameOf(record.getReportedBy())));
        }
        return responses;
    }

    private String nameOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userDirectory.findById(userId).map(UserDirectory.UserSummary::fullName).orElse(null);
    }

    /** G6: who is invigilating this sitting. */
    @Transactional(readOnly = true)
    public List<ExamInvigilatorResponse> invigilatorsFor(UUID sittingId) {
        require(sittingId);
        return examInvigilatorRepository.findByExamSittingIdOrderByAssignedAtAsc(sittingId).stream()
                .map(invigilator -> ExamInvigilatorResponse.from(invigilator, nameOf(invigilator.getUserId())))
                .toList();
    }

    @Transactional
    public List<ExamInvigilatorResponse> assignInvigilator(UUID sittingId, UUID userId) {
        require(sittingId);
        if (!userDirectory.exists(userId)) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.INVIGILATOR_USER_NOT_FOUND, "No user exists with id " + userId);
        }
        if (!examInvigilatorRepository.existsByExamSittingIdAndUserId(sittingId, userId)) {
            examInvigilatorRepository.save(new ExamInvigilator(sittingId, userId, actorId()));
            auditTrail.record(
                    actorId(),
                    AuditTrail.Action.EXAM_INVIGILATOR_ASSIGNED,
                    ENTITY,
                    sittingId,
                    "Assigned invigilator " + userId);
        }
        return invigilatorsFor(sittingId);
    }

    @Transactional
    public List<ExamInvigilatorResponse> unassignInvigilator(UUID sittingId, UUID userId) {
        require(sittingId);
        ExamInvigilator invigilator = examInvigilatorRepository
                .findById(new ExamInvigilator.ExamInvigilatorId(sittingId, userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.INVIGILATOR_NOT_ASSIGNED,
                        "User " + userId + " is not assigned to sitting " + sittingId));
        examInvigilatorRepository.delete(invigilator);
        auditTrail.record(
                actorId(),
                AuditTrail.Action.EXAM_INVIGILATOR_UNASSIGNED,
                ENTITY,
                sittingId,
                "Unassigned invigilator " + userId);
        return invigilatorsFor(sittingId);
    }

    /** G6: who this resit or deferred paper is actually for. Empty means it is visible to the whole section. */
    @Transactional(readOnly = true)
    public List<ExamResitCandidateResponse> resitCandidatesFor(UUID sittingId) {
        require(sittingId);
        return examResitCandidateRepository.findByExamSittingIdOrderByAddedAtAsc(sittingId).stream()
                .map(candidate -> ExamResitCandidateResponse.from(candidate, studentNumberOf(candidate.getStudentId())))
                .toList();
    }

    @Transactional
    public List<ExamResitCandidateResponse> addResitCandidate(UUID sittingId, UUID studentId) {
        require(sittingId);
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.RESIT_CANDIDATE_STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
        if (!examResitCandidateRepository.existsByExamSittingIdAndStudentId(sittingId, studentId)) {
            examResitCandidateRepository.save(new ExamResitCandidate(sittingId, studentId, actorId()));
            auditTrail.record(
                    actorId(),
                    AuditTrail.Action.EXAM_RESIT_CANDIDATE_ADDED,
                    ENTITY,
                    sittingId,
                    "Added resit candidate " + studentId);
        }
        return resitCandidatesFor(sittingId);
    }

    @Transactional
    public List<ExamResitCandidateResponse> removeResitCandidate(UUID sittingId, UUID studentId) {
        require(sittingId);
        ExamResitCandidate candidate = examResitCandidateRepository
                .findById(new ExamResitCandidate.ExamResitCandidateId(sittingId, studentId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.RESIT_CANDIDATE_NOT_FOUND,
                        "Student " + studentId + " is not a resit candidate for sitting " + sittingId));
        examResitCandidateRepository.delete(candidate);
        auditTrail.record(
                actorId(),
                AuditTrail.Action.EXAM_RESIT_CANDIDATE_REMOVED,
                ENTITY,
                sittingId,
                "Removed resit candidate " + studentId);
        return resitCandidatesFor(sittingId);
    }

    private String studentNumberOf(UUID studentId) {
        return studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::studentNumber).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ExamSittingResponse> forSection(UUID sectionId) {
        CourseCatalog.SectionSummary section = courseCatalog.findSection(sectionId).orElse(null);
        List<ExamSittingResponse> responses = new ArrayList<>();
        for (ExamSitting sitting : examSittingRepository.findByCourseSectionIdOrderByStartsAtAsc(sectionId)) {
            responses.add(toResponse(sitting, section));
        }
        return responses;
    }


    /**
     * Every sitting in a term, drafts included — the examinations office plans against the whole
     * picture, and a draft still occupies its hall in that plan.
     *
     * <p>Assembled from the section list rather than a join across module tables, so the exam
     * schema and the course schema stay independent.
     */
    @Transactional(readOnly = true)
    public List<ExamSittingResponse> forTerm(UUID academicTermId) {
        Map<UUID, CourseCatalog.SectionSummary> sections = new LinkedHashMap<>();
        for (CourseCatalog.SectionSummary section : courseCatalog.findSectionsInTerm(academicTermId)) {
            sections.put(section.id(), section);
        }
        if (sections.isEmpty()) {
            return List.of();
        }
        List<ExamSittingResponse> responses = new ArrayList<>();
        for (ExamSitting sitting : examSittingRepository.findByCourseSectionIdInOrderByStartsAtAsc(sections.keySet())) {
            responses.add(toResponse(sitting, sections.get(sitting.getCourseSectionId())));
        }
        return responses;
    }

    /**
     * Refuses a hall that is already hosting an exam then.
     *
     * <p>Compared against every published or draft sitting in the same room — a draft still occupies
     * the hall in the office's plan, and letting two drafts collide silently only defers the problem
     * to the day the second one is published.
     */
    private void requireRoomFree(ExamSitting incoming, UUID ignoringId) {
        String room = incoming.getRoom().trim();
        for (ExamSitting existing : examSittingRepository.findByRoomIgnoreCase(room)) {
            // A cancelled sitting no longer holds its hall — otherwise a withdrawn exam would block
            // its own replacement from being scheduled in the same room.
            if (existing.getId().equals(ignoringId) || existing.isCancelled()) {
                continue;
            }
            if (overlaps(incoming.getStartsAt(), incoming.endsAt(), existing.getStartsAt(), existing.endsAt())) {
                throw new BusinessException(
                        CourseErrorCode.SCHEDULE_CONFLICT,
                        "%s is already hosting %s from %s to %s."
                                .formatted(room, existing.getTitle(), existing.getStartsAt(), existing.endsAt()));
            }
        }
    }

    private static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    private ExamSitting require(UUID sittingId) {
        return examSittingRepository
                .findById(sittingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CourseErrorCode.COURSE_SECTION_NOT_FOUND, "No exam sitting exists with id " + sittingId));
    }

    private ExamSittingResponse toResponse(ExamSitting sitting, CourseCatalog.SectionSummary section) {
        if (section == null) {
            return ExamSittingResponse.from(sitting, "—", "—", "—");
        }
        String title = courseCatalog
                .findCourse(section.courseId())
                .map(CourseCatalog.CourseSummary::title)
                .orElse(section.courseCode());
        return ExamSittingResponse.from(sitting, section.courseCode(), title, section.sectionCode());
    }
}
