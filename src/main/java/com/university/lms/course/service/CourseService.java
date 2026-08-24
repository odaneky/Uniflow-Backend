package com.university.lms.course.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.common.telemetry.UniFlowMetrics;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import java.util.Optional;
import com.university.lms.course.dto.ScheduleCheckRequest;
import com.university.lms.course.dto.ScheduleCheckResponse;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.api.SectionActions;
import com.university.lms.course.api.Timetable;
import com.university.lms.course.domain.Course;
import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseErrorCode;
import com.university.lms.course.domain.CourseRequirementGroup;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.CourseStatus;
import com.university.lms.course.domain.RequirementKind;
import com.university.lms.course.dto.AssignedLecturerResponse;
import com.university.lms.course.dto.CourseResponse;
import com.university.lms.course.dto.CourseSectionResponse;
import com.university.lms.course.dto.CourseSummaryResponse;
import com.university.lms.course.dto.CreateCourseRequest;
import com.university.lms.course.dto.CreateCourseSectionRequest;
import com.university.lms.course.dto.ReplaceCourseRequirementsRequest;
import com.university.lms.course.dto.ReplaceSectionMeetingsRequest;
import com.university.lms.course.dto.RequirementGroupResponse;
import com.university.lms.course.dto.RequirementGroupResponse.RequirementOptionResponse;
import com.university.lms.course.dto.SectionMeetingResponse;
import com.university.lms.course.dto.UpdateCourseRequest;
import com.university.lms.course.dto.UpdateSectionRequest;
import com.university.lms.course.domain.SectionComponent;
import com.university.lms.course.domain.SectionMeeting;
import com.university.lms.course.dto.SectionComponentRequest;
import com.university.lms.course.dto.SectionComponentResponse;
import com.university.lms.course.repository.CourseRepository;
import com.university.lms.course.repository.CourseRequirementGroupRepository;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.course.repository.SectionComponentRepository;
import com.university.lms.course.repository.SectionMeetingRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for the course catalog and its offerings. */
@Service
@Transactional(readOnly = true)
public class CourseService implements SectionActions {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseRequirementGroupRepository requirementGroupRepository;
    private final SectionMeetingRepository sectionMeetingRepository;
    private final SectionComponentRepository sectionComponentRepository;
    private final AcademicStructure academicStructure;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditTrail auditTrail;
    private final UniFlowMetrics metrics;

    private final TeachingConflictChecker conflictChecker;

    public CourseService(
            CourseRepository courseRepository,
            CourseSectionRepository courseSectionRepository,
            CourseRequirementGroupRepository requirementGroupRepository,
            SectionMeetingRepository sectionMeetingRepository,
            SectionComponentRepository sectionComponentRepository,
            AcademicStructure academicStructure,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            AuditTrail auditTrail,
            UniFlowMetrics metrics,
            TeachingConflictChecker conflictChecker) {
        this.courseRepository = courseRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.requirementGroupRepository = requirementGroupRepository;
        this.sectionMeetingRepository = sectionMeetingRepository;
        this.sectionComponentRepository = sectionComponentRepository;
        this.academicStructure = academicStructure;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
        this.auditTrail = auditTrail;
        this.metrics = metrics;
            this.conflictChecker = conflictChecker;
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        if (!academicStructure.departmentExists(request.departmentId())) {
            throw new ResourceNotFoundException(
                    CourseErrorCode.COURSE_DEPARTMENT_NOT_FOUND,
                    "No department exists with id " + request.departmentId());
        }
        if (courseRepository.existsByCourseCodeIgnoreCase(request.courseCode())) {
            throw new ResourceAlreadyExistsException(
                    CourseErrorCode.COURSE_CODE_ALREADY_EXISTS,
                    "Course code " + request.courseCode() + " is already in use");
        }

        Course course = new Course(
                request.courseCode().toUpperCase(Locale.ROOT),
                request.title(),
                request.credits(),
                request.level(),
                request.departmentId(),
                request.components());
        course.describe(request.description());

        try {
            Course saved = courseRepository.saveAndFlush(course);
            log.info("Created course {} ({})", saved.getCourseCode(), saved.getId());
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // The unique index, not the check above, is the real guarantee under concurrency.
            throw new ResourceAlreadyExistsException(
                    CourseErrorCode.COURSE_CODE_ALREADY_EXISTS,
                    "Course code " + request.courseCode() + " is already in use",
                    ex);
        }
    }

    public CourseResponse findById(UUID courseId) {
        return toResponse(require(courseId));
    }

    public CourseResponse findByCourseCode(String courseCode) {
        return courseRepository
                .findByCourseCodeIgnoreCase(courseCode)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CourseErrorCode.COURSE_NOT_FOUND, "No course exists with code " + courseCode));
    }

    public CourseSectionResponse findSection(UUID sectionId) {
        return toSectionResponse(requireSection(sectionId));
    }

    public PageResponse<CourseSummaryResponse> search(
            CourseStatus status, UUID departmentId, String search, Pageable pageable) {
        return PageResponse.from(
                courseRepository.search(status, departmentId, toLikePattern(search), pageable),
                CourseSummaryResponse::from);
    }

    @Transactional
    public CourseResponse update(UUID courseId, UpdateCourseRequest request) {
        Course course = require(courseId);

        if (request.title() != null) {
            course.retitle(request.title());
        }
        if (request.description() != null) {
            course.describe(request.description());
        }
        if (request.credits() != null) {
            course.recredit(request.credits());
        }
        if (request.components() != null) {
            if (request.components().isEmpty()) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_COURSE_STATE, "A course must include at least one component");
            }
            course.replaceComponents(request.components());
        }
        if (request.status() != null) {
            applyStatusChange(course, request.status());
        }
        return toResponse(course);
    }

    /**
     * Replaces every requirement group on the course. An empty list clears them. Groups are ANDed;
     * {@code anyOfCourseIds} inside a group is OR.
     */
    @Transactional
    public CourseResponse replaceRequirements(UUID courseId, ReplaceCourseRequirementsRequest request) {
        Course course = require(courseId);
        List<CourseRequirementGroup> next = new ArrayList<>();
        List<ReplaceCourseRequirementsRequest.RequirementGroupRequest> groups = request.groups();
        for (int i = 0; i < groups.size(); i++) {
            next.add(toGroup(courseId, i, groups.get(i)));
        }
        requirementGroupRepository.deleteByCourseId(courseId);
        requirementGroupRepository.flush();
        requirementGroupRepository.saveAll(next);
        log.info("Replaced {} requirement group(s) on course {}", next.size(), course.getCourseCode());
        return toResponse(course);
    }

    // ---------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------

    @Transactional
    public CourseSectionResponse addSection(UUID courseId, CreateCourseSectionRequest request) {
        Course course = require(courseId);

        if (!course.isOfferable()) {
            throw new BusinessException(
                    CourseErrorCode.COURSE_NOT_OFFERABLE,
                    "Course " + course.getCourseCode() + " is " + course.getStatus()
                            + " and cannot be offered as a section");
        }
        if (!academicStructure.findTerm(request.academicTermId(), java.time.Instant.now()).isPresent()) {
            throw new ResourceNotFoundException(
                    CourseErrorCode.COURSE_SECTION_TERM_NOT_FOUND,
                    "No academic term exists with id " + request.academicTermId());
        }
        List<SectionComponentRequest> offerings = offeringsOf(request);
        requireKnownLecturers(offerings, request.lecturerUserId());
        String requested = request.sectionCode() == null ? "" : request.sectionCode().trim();
        String sectionCode = requested.isEmpty() ? nextOccurrenceCode(courseId) : requested;
        if (courseSectionRepository.existsByCourseIdAndSectionCodeIgnoreCase(courseId, sectionCode)) {
            throw new ResourceAlreadyExistsException(
                    CourseErrorCode.COURSE_SECTION_ALREADY_EXISTS,
                    "Section " + sectionCode + " already exists for this course");
        }

        CourseSection section = new CourseSection(
                course,
                request.academicTermId(),
                sectionCode,
                enrolmentCapacity(offerings, request.capacity()),
                primaryComponent(offerings));
        UUID lecturerUserId = primaryLecturer(offerings, request.lecturerUserId());
        if (lecturerUserId != null) {
            section.assignLecturer(lecturerUserId);
        }

        try {
            CourseSection saved = courseSectionRepository.saveAndFlush(section);
            replaceComponents(saved, offerings);
            recordOccurrence(AuditTrail.Action.OCCURRENCE_CREATED, saved);
            metrics.occurrence("created");
            log.info("Created section {} for course {}", saved.getSectionCode(), course.getCourseCode());
            return toSectionResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    CourseErrorCode.COURSE_SECTION_ALREADY_EXISTS,
                    "Section " + sectionCode + " already exists for this course",
                    ex);
        }
    }

    /** Next unused {@code UNn} on this course. Other courses may also have UN1. */
    private String nextOccurrenceCode(UUID courseId) {
        Set<String> used = new LinkedHashSet<>();
        for (CourseSection row : courseSectionRepository.findByCourseId(courseId)) {
            used.add(row.getSectionCode().toUpperCase(Locale.ROOT));
        }
        int n = 1;
        while (used.contains("UN" + n)) {
            n += 1;
        }
        return "UN" + n;
    }

    public List<CourseSectionResponse> findSections(UUID courseId) {
        require(courseId);
        return courseSectionRepository.findByCourseId(courseId).stream()
                .map(this::toSectionResponse)
                .toList();
    }

    /** Lecturer accounts for assignment pickers — the LECTURER role, plus anyone already on an occurrence. */
    public List<AssignedLecturerResponse> listAssignedLecturers() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (UserDirectory.UserSummary lecturer : userDirectory.findByRealmRole(SecurityRoles.LECTURER)) {
            counts.putIfAbsent(lecturer.id(), 0);
        }
        for (CourseSection section : courseSectionRepository.findAssignedWithCourse()) {
            tallyLecturer(counts, seen, section.getLecturerUserId(), section.getCourse().getId());
        }
        for (SectionComponent row : sectionComponentRepository.findAssignedWithSection()) {
            tallyLecturer(counts, seen, row.getLecturerUserId(), row.getSection().getCourse().getId());
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    UserDirectory.UserSummary user = userDirectory.findById(entry.getKey()).orElse(null);
                    return new AssignedLecturerResponse(
                            entry.getKey(),
                            user == null ? "Lecturer" : user.fullName(),
                            user == null ? null : user.email(),
                            entry.getValue());
                })
                .sorted(Comparator.comparing(AssignedLecturerResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Sections where this user is the primary lecturer or a component lecturer. */
    public List<CourseSectionResponse> listLecturerSections(UUID lecturerUserId) {
        if (lecturerUserId == null) {
            return List.of();
        }
        LinkedHashMap<UUID, CourseSection> byId = new LinkedHashMap<>();
        for (CourseSection section : courseSectionRepository.findByLecturerUserIdWithCourse(lecturerUserId)) {
            byId.put(section.getId(), section);
        }
        for (SectionComponent row : sectionComponentRepository.findByLecturerUserIdWithSection(lecturerUserId)) {
            CourseSection section = row.getSection();
            byId.putIfAbsent(section.getId(), section);
        }
        return byId.values().stream()
                .sorted(Comparator.comparing((CourseSection s) -> s.getCourse().getCourseCode())
                        .thenComparing(CourseSection::getSectionCode))
                .map(this::toSectionResponse)
                .toList();
    }

    @Transactional
    public CourseSectionResponse openSection(UUID sectionId) {
        CourseSection section = requireSection(sectionId);
        section.open();
        return toSectionResponse(section);
    }

    @Transactional
    public CourseSectionResponse closeSection(UUID sectionId) {
        CourseSection section = requireSection(sectionId);
        section.close();
        return toSectionResponse(section);
    }

    @Transactional
    public CourseSectionResponse cancelSection(UUID sectionId) {
        CourseSection section = requireSection(sectionId);
        try {
            section.cancel();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CourseErrorCode.INVALID_SECTION_STATE, ex.getMessage());
        }
        recordOccurrence(AuditTrail.Action.OCCURRENCE_CANCELLED, section);
        metrics.occurrence("cancelled");
        log.info("Cancelled section {} of {}", section.getSectionCode(), section.getCourse().getCourseCode());
        return toSectionResponse(section);
    }

    /**
     * {@link SectionActions#cancel} — cancels the section only. Enrolled students are untouched
     * here; releasing their seats, reversing charges and notifying them is orchestrated from
     * {@code enrollment.service.SectionCancellationService}, which is what a caller reaches this
     * through in practice (see that class for why the split exists).
     */
    @Override
    @Transactional
    public void cancel(UUID sectionId) {
        cancelSection(sectionId);
    }

    @Transactional
    public CourseSectionResponse updateSection(UUID sectionId, UpdateSectionRequest request) {
        CourseSection section = requireSection(sectionId);
        if (request.components() != null) {
            List<SectionComponentRequest> offerings = distinctComponents(request.components());
            requireKnownLecturers(offerings, null);
            replaceComponents(section, offerings);
            UUID lecturerUserId = primaryLecturer(offerings, section.getLecturerUserId());
            if (lecturerUserId != null) {
                section.assignLecturer(lecturerUserId);
            }
        }
        Integer nextCapacity = request.capacity();
        if (nextCapacity == null && request.components() != null) {
            nextCapacity = enrolmentCapacity(request.components(), section.getCapacity());
        }
        if (nextCapacity != null) {
            try {
                section.changeCapacity(nextCapacity);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(CourseErrorCode.INVALID_SECTION_STATE, ex.getMessage());
            }
        }
        recordOccurrence(AuditTrail.Action.OCCURRENCE_UPDATED, section);
        metrics.occurrence("updated");
        return toSectionResponse(section);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        CourseSection section = requireSection(sectionId);
        if (section.getEnrolledCount() > 0) {
            throw new BusinessException(
                    CourseErrorCode.COURSE_SECTION_IN_USE,
                    "Cannot delete an occurrence with enrolments; cancel it instead");
        }
        try {
            courseSectionRepository.delete(section);
            courseSectionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                    CourseErrorCode.COURSE_SECTION_IN_USE,
                    "This occurrence still has linked records; cancel it instead");
        }
        log.info("Deleted section {} of {}", section.getSectionCode(), section.getCourse().getCourseCode());
    }

    /**
     * Reports what would clash, without writing anything.
     *
     * <p>Deliberately the same checker the write path uses. A preview that computed conflicts
     * independently would eventually disagree with the rule it previews, and the disagreement shows
     * up as a save that fails with no warning beforehand — the worst of both.
     */
    @Transactional(readOnly = true)
    public ScheduleCheckResponse checkSchedule(UUID sectionId, ScheduleCheckRequest request) {
        CourseSection section = requireSection(sectionId);

        List<CourseCatalog.Meeting> proposed = request.meetings() == null
                ? toCatalogMeetings(sectionMeetingRepository.findBySectionIdOrderByPositionAsc(sectionId))
                : request.meetings().stream().map(CourseService::toCatalogMeeting).toList();

        List<ScheduleCheckResponse.Conflict> conflicts = new ArrayList<>();

        if (request.lecturerUserId() != null) {
            // Asked about a specific person: they would take the whole section, so every proposed
            // meeting is theirs. This mirrors assignLecturer.
            conflictChecker
                    .lecturerClash(request.lecturerUserId(), section, proposed)
                    .ifPresent(message ->
                            conflicts.add(new ScheduleCheckResponse.Conflict(ScheduleCheckResponse.Kind.LECTURER, message)));
        } else {
            // No one named: check whoever is already attached, each against what they actually take.
            for (UUID lecturerUserId : conflictChecker.lecturersOf(section)) {
                conflictChecker
                        .lecturerClash(
                                lecturerUserId,
                                section,
                                conflictChecker.meetingsTaughtBy(lecturerUserId, section, proposed))
                        .ifPresent(message -> conflicts.add(new ScheduleCheckResponse.Conflict(
                                ScheduleCheckResponse.Kind.LECTURER, message)));
            }
        }

        conflictChecker
                .roomClash(section, proposed)
                .ifPresent(message ->
                        conflicts.add(new ScheduleCheckResponse.Conflict(ScheduleCheckResponse.Kind.ROOM, message)));

        return ScheduleCheckResponse.of(conflicts);
    }


    /** Every section running in a term, for staff tools that work across the whole term. */
    @Transactional(readOnly = true)
    public List<CourseSectionResponse> sectionsInTerm(UUID academicTermId) {
        return courseSectionRepository.findByAcademicTermId(academicTermId).stream()
                .map(this::toSectionResponse)
                .toList();
    }

    @Transactional
    public CourseSectionResponse assignLecturer(UUID sectionId, UUID lecturerUserId) {
        return assignLecturer(sectionId, lecturerUserId, false);
    }

    /**
     * Assigns a lecturer, refusing if that would double-book them.
     *
     * <p>Checked against everything they already teach in the same term — on a whole section or on
     * one component of one. Existence of the user was already checked; being free was not, so the
     * same person could be put in two rooms at nine on Monday and nothing objected.
     */
    @Transactional
    public CourseSectionResponse assignLecturer(UUID sectionId, UUID lecturerUserId, boolean allowConflicts) {
        CourseSection section = requireSection(sectionId);
        if (!userDirectory.exists(lecturerUserId)) {
            throw new ResourceNotFoundException(
                    CourseErrorCode.COURSE_SECTION_NOT_FOUND, "No user exists with id " + lecturerUserId);
        }

        // Assigning a lecturer to the section makes every one of its meetings theirs. Deriving the
        // set from the section instead would find nothing — it does not name them yet — and pass.
        List<CourseCatalog.Meeting> meetings =
                toCatalogMeetings(sectionMeetingRepository.findBySectionIdOrderByPositionAsc(sectionId));
        Optional<String> clash = conflictChecker.lecturerClash(lecturerUserId, section, meetings);
        if (clash.isPresent()) {
            if (!allowConflicts) {
                throw new BusinessException(CourseErrorCode.SCHEDULE_CONFLICT, clash.get());
            }
            recordConflictOverride(section, "lecturer", clash.get());
        }

        section.assignLecturer(lecturerUserId);
        return toSectionResponse(section);
    }

    /**
     * Records that somebody knowingly scheduled a clash.
     *
     * <p>The override exists because timetabling has real exceptions; the audit exists because an
     * override that leaves no trace is indistinguishable from a bug when someone asks later why two
     * classes are in one room.
     */
    private void recordConflictOverride(CourseSection section, String kind, String detail) {
        log.warn(
                "Schedule conflict overridden on section {} ({}): {}",
                section.getId(),
                kind,
                detail);
        auditTrail.record(
                currentUserProvider.find().map(CurrentUser::userId).orElse(null),
                AuditTrail.Action.SCHEDULE_CONFLICT_OVERRIDDEN,
                "CourseSection",
                section.getId(),
                kind + " conflict overridden: " + detail);
    }

    @Transactional
    public CourseSectionResponse replaceMeetings(UUID sectionId, ReplaceSectionMeetingsRequest request) {
        CourseSection section = requireSection(sectionId);
        List<SectionMeeting> next = new ArrayList<>();
        List<ReplaceSectionMeetingsRequest.MeetingRequest> meetings = request.meetings();
        List<CourseCatalog.Meeting> incoming = new ArrayList<>();
        for (int i = 0; i < meetings.size(); i++) {
            var spec = meetings.get(i);
            if (!spec.endTime().isAfter(spec.startTime())) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_MEETING,
                        "Each session must end after it starts on the same day. Overnight times are not supported.");
            }
            next.add(new SectionMeeting(
                    section,
                    spec.dayOfWeek(),
                    spec.startTime(),
                    spec.endTime(),
                    spec.room(),
                    spec.sessionType(),
                    i));
            incoming.add(toCatalogMeeting(spec));
        }
        if (Timetable.selfClash(incoming)) {
            throw new BusinessException(
                    CourseErrorCode.INVALID_MEETING, "Sessions on this occurrence overlap.");
        }
        List<List<CourseCatalog.Meeting>> courseMeetings = new ArrayList<>();
        for (CourseSection sibling :
                courseSectionRepository.findByCourseIdAndAcademicTermId(
                        section.getCourse().getId(), section.getAcademicTermId())) {
            if (sibling.getId().equals(sectionId)) {
                courseMeetings.add(incoming);
            } else {
                courseMeetings.add(toCatalogMeetings(
                        sectionMeetingRepository.findBySectionIdOrderByPositionAsc(sibling.getId())));
            }
        }
        Timetable.componentOrderIssue(courseMeetings.isEmpty() ? List.of(incoming) : courseMeetings)
                .ifPresent(message -> {
                    throw new BusinessException(CourseErrorCode.INVALID_MEETING, message);
                });
        // Re-checked here, not only when a lecturer is assigned. Otherwise the check is bypassable
        // by ordering: assign cleanly first, then move the sessions on top of another class.
        for (UUID lecturerUserId : conflictChecker.lecturersOf(section)) {
            Optional<String> clash = conflictChecker.lecturerClash(
                    lecturerUserId, section, conflictChecker.meetingsTaughtBy(lecturerUserId, section, incoming));
            if (clash.isPresent()) {
                if (!request.overrideRequested()) {
                    throw new BusinessException(CourseErrorCode.SCHEDULE_CONFLICT, clash.get());
                }
                recordConflictOverride(section, "lecturer", clash.get());
            }
        }
        Optional<String> roomClash = conflictChecker.roomClash(section, incoming);
        if (roomClash.isPresent()) {
            if (!request.overrideRequested()) {
                throw new BusinessException(CourseErrorCode.SCHEDULE_CONFLICT, roomClash.get());
            }
            recordConflictOverride(section, "room", roomClash.get());
        }

        sectionMeetingRepository.deleteBySectionId(sectionId);
        sectionMeetingRepository.flush();
        try {
            sectionMeetingRepository.saveAll(next);
            sectionMeetingRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (detail.contains("ck_section_meetings_span")) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_MEETING,
                        "Each session must end after it starts on the same day. Overnight times are not supported.",
                        ex);
            }
            throw new BusinessException(
                    CourseErrorCode.INVALID_MEETING, "Could not save the session timetable.", ex);
        }
        recordOccurrence(AuditTrail.Action.OCCURRENCE_UPDATED, section);
        metrics.occurrence("updated");
        return toSectionResponse(section);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * A retired course is terminal: bringing it back would leave sections and results attached to
     * a definition that was formally withdrawn.
     */
    private void applyStatusChange(Course course, CourseStatus target) {
        if (course.getStatus() == target) {
            return;
        }
        if (course.getStatus() == CourseStatus.RETIRED) {
            throw new BusinessException(
                    CourseErrorCode.INVALID_COURSE_STATE, "A retired course cannot be moved back to " + target);
        }
        switch (target) {
            case ACTIVE -> course.activate();
            case RETIRED -> course.retire();
            case DRAFT -> throw new BusinessException(
                    CourseErrorCode.INVALID_COURSE_STATE, "A course cannot be returned to DRAFT once approved");
        }
    }

    private static CourseCatalog.Meeting toCatalogMeeting(ReplaceSectionMeetingsRequest.MeetingRequest spec) {
        return new CourseCatalog.Meeting(
                spec.dayOfWeek(),
                SectionMeetingResponse.dayName(spec.dayOfWeek()),
                CLOCK.format(spec.startTime()),
                CLOCK.format(spec.endTime()),
                spec.room(),
                spec.sessionType());
    }

    private static List<CourseCatalog.Meeting> toCatalogMeetings(List<SectionMeeting> rows) {
        List<CourseCatalog.Meeting> meetings = new ArrayList<>();
        for (SectionMeeting row : rows) {
            meetings.add(new CourseCatalog.Meeting(
                    row.getDayOfWeek(),
                    SectionMeetingResponse.dayName(row.getDayOfWeek()),
                    CLOCK.format(row.getStartTime()),
                    CLOCK.format(row.getEndTime()),
                    row.getRoom(),
                    row.getSessionType()));
        }
        return meetings;
    }

    private Course require(UUID courseId) {
        return courseRepository
                .findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CourseErrorCode.COURSE_NOT_FOUND, "No course exists with id " + courseId));
    }

    private CourseSection requireSection(UUID sectionId) {
        return courseSectionRepository
                .findByIdWithCourse(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CourseErrorCode.COURSE_SECTION_NOT_FOUND, "No course section exists with id " + sectionId));
    }

    private CourseRequirementGroup toGroup(
            UUID courseId, int position, ReplaceCourseRequirementsRequest.RequirementGroupRequest spec) {
        if (spec.kind() == RequirementKind.MINIMUM_LEVEL) {
            if (spec.minimumLevel() == null) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_REQUIREMENT_GROUP, "MINIMUM_LEVEL requires minimumLevel");
            }
            if (spec.anyOfCourseIds() != null && !spec.anyOfCourseIds().isEmpty()) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_REQUIREMENT_GROUP, "MINIMUM_LEVEL cannot name courses");
            }
            return new CourseRequirementGroup(courseId, position, spec.kind(), spec.minimumLevel(), Set.of());
        }
        List<UUID> optionIds = spec.anyOfCourseIds() == null ? List.of() : spec.anyOfCourseIds();
        if (optionIds.isEmpty()) {
            throw new BusinessException(
                    CourseErrorCode.INVALID_REQUIREMENT_GROUP, spec.kind() + " requires at least one course");
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(optionIds);
        for (UUID optionId : unique) {
            if (optionId.equals(courseId)) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_REQUIREMENT_GROUP, "A course cannot require itself");
            }
            if (!courseRepository.existsById(optionId)) {
                throw new ResourceNotFoundException(
                        CourseErrorCode.REQUIREMENT_COURSE_UNKNOWN, "No course exists with id " + optionId);
            }
        }
        return new CourseRequirementGroup(courseId, position, spec.kind(), null, unique);
    }

    private CourseResponse toResponse(Course course) {
        List<RequirementGroupResponse> groups = requirementGroupRepository
                .findByCourseIdOrderByPositionAsc(course.getId())
                .stream()
                .map(this::toGroupResponse)
                .toList();
        return CourseResponse.from(course, groups);
    }

    private RequirementGroupResponse toGroupResponse(CourseRequirementGroup group) {
        List<RequirementOptionResponse> options = group.getOptionCourseIds().stream()
                .map(id -> courseRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(option -> new RequirementOptionResponse(
                        option.getId(), option.getCourseCode(), option.getTitle()))
                .toList();
        return new RequirementGroupResponse(group.getId(), group.getKind(), group.getMinimumLevel(), options);
    }

    private CourseSectionResponse toSectionResponse(CourseSection section) {
        List<SectionComponentResponse> components = sectionComponentRepository.findBySectionId(section.getId()).stream()
                .sorted(Comparator.comparingInt(row -> row.getComponent().ordinal()))
                .map(row -> new SectionComponentResponse(
                        row.getId(),
                        row.getComponent(),
                        row.getCapacity(),
                        row.getLecturerUserId(),
                        lecturerName(row.getLecturerUserId())))
                .toList();
        return CourseSectionResponse.from(
                section,
                lecturerName(section.getLecturerUserId()),
                sectionMeetingRepository.findBySectionIdOrderByPositionAsc(section.getId()),
                components);
    }

    private String lecturerName(UUID lecturerUserId) {
        if (lecturerUserId == null) {
            return null;
        }
        return userDirectory.findById(lecturerUserId).map(UserDirectory.UserSummary::fullName).orElse(null);
    }

    private List<SectionComponentRequest> offeringsOf(CreateCourseSectionRequest request) {
        if (request.components() != null && !request.components().isEmpty()) {
            return distinctComponents(request.components());
        }
        return List.of(new SectionComponentRequest(
                request.component() == null ? CourseComponent.LECTURE : request.component(),
                request.capacity(),
                request.lecturerUserId()));
    }

    private List<SectionComponentRequest> distinctComponents(List<SectionComponentRequest> offerings) {
        if (offerings.isEmpty()) {
            throw new BusinessException(
                    CourseErrorCode.INVALID_SECTION_STATE, "An occurrence must include at least one component");
        }
        Set<CourseComponent> seen = new LinkedHashSet<>();
        for (SectionComponentRequest offering : offerings) {
            if (!seen.add(offering.component())) {
                throw new BusinessException(
                        CourseErrorCode.INVALID_SECTION_STATE,
                        "An occurrence can hold each component only once");
            }
        }
        return List.copyOf(offerings);
    }

    private void requireKnownLecturers(List<SectionComponentRequest> offerings, UUID fallbackLecturerUserId) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (fallbackLecturerUserId != null) {
            ids.add(fallbackLecturerUserId);
        }
        for (SectionComponentRequest offering : offerings) {
            if (offering.lecturerUserId() != null) {
                ids.add(offering.lecturerUserId());
            }
        }
        for (UUID lecturerUserId : ids) {
            if (!userDirectory.exists(lecturerUserId)) {
                throw new ResourceNotFoundException(
                        CourseErrorCode.COURSE_SECTION_NOT_FOUND, "No user exists with id " + lecturerUserId);
            }
        }
    }

    private void replaceComponents(CourseSection section, List<SectionComponentRequest> offerings) {
        sectionComponentRepository.deleteBySectionId(section.getId());
        sectionComponentRepository.flush();
        for (SectionComponentRequest offering : offerings) {
            sectionComponentRepository.save(new SectionComponent(
                    section, offering.component(), offering.capacity(), offering.lecturerUserId()));
        }
        sectionComponentRepository.flush();
    }

    private static CourseComponent primaryComponent(List<SectionComponentRequest> offerings) {
        return offerings.stream()
                .map(SectionComponentRequest::component)
                .filter(component -> component == CourseComponent.LECTURE)
                .findFirst()
                .orElse(offerings.get(0).component());
    }

    private static UUID primaryLecturer(List<SectionComponentRequest> offerings, UUID fallback) {
        UUID lectureLecturer = offerings.stream()
                .filter(offering -> offering.component() == CourseComponent.LECTURE)
                .map(SectionComponentRequest::lecturerUserId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (lectureLecturer != null) {
            return lectureLecturer;
        }
        return offerings.stream()
                .map(SectionComponentRequest::lecturerUserId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    private static int enrolmentCapacity(List<SectionComponentRequest> offerings, int fallback) {
        return offerings.stream()
                .map(SectionComponentRequest::capacity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(fallback);
    }

    private static void tallyLecturer(Map<UUID, Integer> counts, Set<String> seen, UUID lecturerUserId, UUID courseId) {
        if (lecturerUserId == null || courseId == null) {
            return;
        }
        if (seen.add(lecturerUserId + ":" + courseId)) {
            counts.merge(lecturerUserId, 1, Integer::sum);
        }
    }

    /**
     * Turns a user-supplied search term into a complete LIKE pattern, or null when there is nothing
     * to filter on.
     *
     * <p>Built here rather than in SQL for two reasons. It is done once per query instead of once
     * per row, and — the reason it is not merely a preference — concatenating inside the query
     * leaves the parameter untyped, which PostgreSQL resolves to {@code bytea} and then fails on.
     * See {@code CourseRepository.search}.
     *
     * <p>{@code %} and {@code _} are wildcards in LIKE, so a term containing them is escaped with
     * {@code !} (matching the {@code escape '!'} clause in the query). Without this, searching for
     * "50%" would match every course rather than the one the user meant. The escape character
     * itself is escaped first, or a literal {@code !} in the term would corrupt the pattern.
     */
    private static String toLikePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private void recordOccurrence(String action, CourseSection section) {
        CurrentUser actor = currentUserProvider.find().orElse(null);
        auditTrail.record(
                actor == null ? null : actor.userId(),
                actor == null ? null : actorLabel(actor),
                action,
                AuditTrail.EntityType.COURSE_SECTION,
                section.getId(),
                section.getCourse().getCourseCode() + " " + section.getSectionCode());
    }

    private static String actorLabel(CurrentUser actor) {
        if (actor.fullName() != null && !actor.fullName().isBlank()) {
            return actor.fullName();
        }
        return actor.username();
    }

}
