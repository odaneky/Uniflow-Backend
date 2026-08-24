package com.university.lms.course.service;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.domain.CourseRequirementGroup;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.CourseSectionStatus;
import com.university.lms.course.domain.RequirementKind;
import com.university.lms.course.domain.SectionComponent;
import com.university.lms.course.domain.SectionMeeting;
import com.university.lms.course.dto.SectionMeetingResponse;
import com.university.lms.course.repository.CourseRepository;
import com.university.lms.course.repository.CourseRequirementGroupRepository;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.course.repository.SectionComponentRepository;
import com.university.lms.course.repository.SectionMeetingRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adapts the course module's internals to its published {@link CourseCatalog} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultCourseCatalog implements CourseCatalog {

    private static final Logger log = LoggerFactory.getLogger(DefaultCourseCatalog.class);

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseRequirementGroupRepository requirementGroupRepository;
    private final SectionMeetingRepository sectionMeetingRepository;
    private final SectionComponentRepository sectionComponentRepository;

    public DefaultCourseCatalog(
            CourseRepository courseRepository,
            CourseSectionRepository courseSectionRepository,
            CourseRequirementGroupRepository requirementGroupRepository,
            SectionMeetingRepository sectionMeetingRepository,
            SectionComponentRepository sectionComponentRepository) {
        this.courseRepository = courseRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.requirementGroupRepository = requirementGroupRepository;
        this.sectionMeetingRepository = sectionMeetingRepository;
        this.sectionComponentRepository = sectionComponentRepository;
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseId != null && courseRepository.existsById(courseId);
    }

    @Override
    public Optional<CourseSummary> findCourse(UUID courseId) {
        if (courseId == null) {
            return Optional.empty();
        }
        return courseRepository
                .findById(courseId)
                .map(course -> new CourseSummary(
                        course.getId(),
                        course.getCourseCode(),
                        course.getTitle(),
                        course.getCredits(),
                        course.getLevel(),
                        course.isOfferable()));
    }

    @Override
    public Optional<SectionSummary> findSection(UUID sectionId) {
        if (sectionId == null) {
            return Optional.empty();
        }
        return courseSectionRepository.findByIdWithCourse(sectionId).map(this::toSummary);
    }

    @Override
    public List<SectionSummary> findSectionsTaughtBy(UUID lecturerUserId) {
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
        return byId.values().stream().map(this::toSummary).toList();
    }


    @Override
    public List<SectionSummary> findSectionsInTerm(UUID academicTermId) {
        if (academicTermId == null) {
            return List.of();
        }
        List<SectionSummary> sections = new ArrayList<>();
        for (CourseSection section : courseSectionRepository.findByAcademicTermId(academicTermId)) {
            sections.add(toSummary(section));
        }
        return sections;
    }

    @Override
    public boolean teaches(UUID lecturerUserId, UUID sectionId) {
        if (lecturerUserId == null || sectionId == null) {
            return false;
        }
        if (lecturerUserId.equals(
                courseSectionRepository.findById(sectionId).map(CourseSection::getLecturerUserId).orElse(null))) {
            return true;
        }
        return sectionComponentRepository.existsBySectionIdAndLecturerUserId(sectionId, lecturerUserId);
    }

    @Override
    public List<Meeting> meetingsOf(UUID sectionId) {
        if (sectionId == null) {
            return List.of();
        }
        return sectionMeetingRepository.findBySectionIdOrderByPositionAsc(sectionId).stream()
                .map(DefaultCourseCatalog::toMeeting)
                .toList();
    }

    private static Meeting toMeeting(SectionMeeting row) {
        return new Meeting(
                row.getDayOfWeek(),
                SectionMeetingResponse.dayName(row.getDayOfWeek()),
                CLOCK.format(row.getStartTime()),
                CLOCK.format(row.getEndTime()),
                row.getRoom(),
                row.getSessionType());
    }

    @Override
    public List<RequirementClause> requirementsOf(UUID courseId) {
        if (courseId == null) {
            return List.of();
        }
        return requirementGroupRepository.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(this::toClause)
                .toList();
    }

    @Override
    public List<String> unmetRequirements(
            UUID courseId, Set<UUID> completedCourseIds, Set<UUID> inProgressCourseIds, int highestCompletedLevel) {
        Set<UUID> completed = completedCourseIds == null ? Set.of() : completedCourseIds;
        Set<UUID> inProgress = inProgressCourseIds == null ? Set.of() : inProgressCourseIds;
        List<String> unmet = new ArrayList<>();
        for (CourseRequirementGroup group : requirementGroupRepository.findByCourseIdOrderByPositionAsc(courseId)) {
            if (group.getKind() == RequirementKind.MINIMUM_LEVEL) {
                int needed = group.getMinimumLevel() == null ? 0 : group.getMinimumLevel();
                if (highestCompletedLevel < needed) {
                    unmet.add("Requires Level " + needed + " standing");
                }
                continue;
            }
            if (group.getOptionCourseIds().isEmpty()) {
                continue;
            }
            Set<UUID> pool = group.getKind() == RequirementKind.COREQUISITE
                    ? concat(completed, inProgress)
                    : completed;
            if (group.getOptionCourseIds().stream().noneMatch(pool::contains)) {
                unmet.add(label(group));
            }
        }
        return unmet;
    }

    private RequirementClause toClause(CourseRequirementGroup group) {
        List<CourseSummary> options = group.getOptionCourseIds().stream()
                .map(this::findCourse)
                .flatMap(Optional::stream)
                .toList();
        return new RequirementClause(
                group.getKind().name(), group.getMinimumLevel(), options);
    }

    private String label(CourseRequirementGroup group) {
        String joined = group.getOptionCourseIds().stream()
                .map(this::findCourse)
                .flatMap(Optional::stream)
                .map(CourseSummary::courseCode)
                .collect(Collectors.joining(" or "));
        if (joined.isBlank()) {
            joined = "a listed course";
        }
        return group.getKind() == RequirementKind.COREQUISITE
                ? "Co-requisite: " + joined + " (may be taken in the same term)"
                : "Prerequisite: " + joined;
    }

    private static Set<UUID> concat(Set<UUID> completed, Set<UUID> inProgress) {
        return java.util.stream.Stream.concat(completed.stream(), inProgress.stream()).collect(Collectors.toSet());
    }

    private SectionSummary toSummary(CourseSection section) {
        return new SectionSummary(
                section.getId(),
                section.getCourse().getId(),
                section.getCourse().getCourseCode(),
                section.getCourse().getTitle(),
                section.getAcademicTermId(),
                section.getSectionCode(),
                section.getCapacity(),
                section.getEnrolledCount(),
                section.isOpenForEnrolment(),
                section.getLecturerUserId(),
                section.isRequiresApproval());
    }

    /**
     * Joins the caller's transaction ({@code MANDATORY}) rather than starting its own. If this
     * opened a separate transaction, the seat could commit while the enrolment that justified it
     * rolled back, permanently leaking capacity. Requiring an ambient transaction turns that
     * mistake into a startup-time failure instead of a slow capacity leak in production.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryReserveSeat(UUID sectionId) {
        if (sectionId == null) {
            return false;
        }
        int updated = courseSectionRepository.reserveSeat(sectionId, CourseSectionStatus.OPEN.name());
        if (updated == 0) {
            log.debug("Seat reservation refused for section {}", sectionId);
        }
        return updated == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSeat(UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        courseSectionRepository.releaseSeat(sectionId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceEnrolledCount(UUID sectionId, int occupyingSeats) {
        if (sectionId == null || occupyingSeats < 0) {
            return;
        }
        courseSectionRepository.replaceEnrolledCount(sectionId, occupyingSeats);
    }
}
