package com.university.lms.course.service;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.api.Timetable;
import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.SectionComponent;
import com.university.lms.course.domain.SectionMeeting;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.course.repository.SectionComponentRepository;
import com.university.lms.course.repository.SectionMeetingRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Answers "is anyone or anywhere already busy then".
 *
 * <p>Two questions, deliberately separate from the ones the timetable already answered. Sessions
 * clashing <em>within</em> one occurrence, and the Lecture→Tutorial→Lab ordering, were already
 * checked; a student's own timetable was checked at enrolment. Nothing checked whether the person
 * being asked to teach, or the room being booked, was free.
 *
 * <h2>Which meetings a lecturer actually teaches</h2>
 *
 * <p>Meetings hang off the <b>section</b> and carry a {@code sessionType}; a lecturer can be
 * attached either to the whole section or to one {@link CourseComponent} of it. So a tutor assigned
 * to the tutorial component teaches only that section's tutorial meetings — comparing them against
 * the lecture would report a clash with a class they do not take. That mapping is the one part of
 * this worth reading carefully.
 *
 * <p>Everything is scoped to a single academic term. Without that, a Monday 09:00 class from three
 * years ago would clash with this year's.
 */
@Component
class TeachingConflictChecker {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");

    private final CourseSectionRepository courseSectionRepository;
    private final SectionComponentRepository sectionComponentRepository;
    private final SectionMeetingRepository sectionMeetingRepository;

    TeachingConflictChecker(
            CourseSectionRepository courseSectionRepository,
            SectionComponentRepository sectionComponentRepository,
            SectionMeetingRepository sectionMeetingRepository) {
        this.courseSectionRepository = courseSectionRepository;
        this.sectionComponentRepository = sectionComponentRepository;
        this.sectionMeetingRepository = sectionMeetingRepository;
    }

    /**
     * A clash between what this lecturer would teach on {@code target} and what they already teach
     * elsewhere in the same term.
     *
     * <p>The caller states which meetings are theirs rather than letting this work it out, because
     * the two callers know different things. When a lecturer is being <em>assigned</em>, the section
     * does not name them yet — deriving it from the record would find nothing and silently pass
     * every double-booking. When meetings are being <em>replaced</em>, the assignment already exists
     * and may cover only one component.
     *
     * @param mine the meetings this lecturer would teach on the target section, after the change
     */
    Optional<String> lecturerClash(UUID lecturerUserId, CourseSection target, List<CourseCatalog.Meeting> mine) {
        if (lecturerUserId == null || target.getAcademicTermId() == null || mine.isEmpty()) {
            return Optional.empty();
        }

        for (CourseSection other : otherSectionsTaughtBy(lecturerUserId, target)) {
            List<SectionMeeting> rows = sectionMeetingRepository.findBySectionIdOrderByPositionAsc(other.getId());
            List<CourseCatalog.Meeting> theirs = restrictToWhatTheyTeach(lecturerUserId, other, toMeetings(rows));
            for (CourseCatalog.Meeting a : mine) {
                for (CourseCatalog.Meeting b : theirs) {
                    if (Timetable.overlaps(a, b)) {
                        return Optional.of("This lecturer already teaches %s %s (%s) at %s %s–%s."
                                .formatted(
                                        other.getCourse().getCourseCode(),
                                        other.getSectionCode(),
                                        b.sessionType(),
                                        b.day(),
                                        b.startTime(),
                                        b.endTime()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Every lecturer attached to a section, whether on the section itself or on a component. */
    Set<UUID> lecturersOf(CourseSection section) {
        Set<UUID> lecturers = new LinkedHashSet<>();
        if (section.getLecturerUserId() != null) {
            lecturers.add(section.getLecturerUserId());
        }
        for (SectionComponent component : sectionComponentRepository.findBySectionId(section.getId())) {
            if (component.getLecturerUserId() != null) {
                lecturers.add(component.getLecturerUserId());
            }
        }
        return lecturers;
    }

    /**
     * A room booked by another section at the same time, in the same term.
     *
     * <p>Rooms are free text, so this matches case- and whitespace-insensitively and also strips
     * the punctuation two staff members type differently for the same room — "Lab 3", "Lab-3" and
     * "LAB_3" are all the same room to everyone except a string comparison. This is a normalization
     * fix, not a room registry: two genuinely different rooms whose names collide once punctuation
     * is stripped would still be treated as one, which is why {@code G1} calls for a real
     * {@code Room} entity as the complete fix — this is the safe, additive slice of it.
     */
    Optional<String> roomClash(CourseSection target, List<CourseCatalog.Meeting> proposed) {
        if (target.getAcademicTermId() == null) {
            return Optional.empty();
        }
        for (CourseCatalog.Meeting incoming : proposed) {
            String room = normaliseRoom(incoming.room());
            if (room == null) {
                continue;
            }
            // Narrowed by room in the query rather than scanning the term: an admin action should
            // not trigger a table scan over every meeting in the university.
            for (SectionMeeting booked :
                    sectionMeetingRepository.findByTermAndRoom(target.getAcademicTermId(), room)) {
                if (booked.getSection().getId().equals(target.getId())) {
                    continue;
                }
                if (Timetable.overlaps(incoming, toMeeting(booked))) {
                    return Optional.of("%s is already booked by %s %s at %s %s–%s."
                            .formatted(
                                    incoming.room(),
                                    booked.getSection().getCourse().getCourseCode(),
                                    booked.getSection().getSectionCode(),
                                    incoming.day(),
                                    CLOCK.format(booked.getStartTime()),
                                    CLOCK.format(booked.getEndTime())));
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private List<CourseSection> otherSectionsTaughtBy(UUID lecturerUserId, CourseSection target) {
        List<CourseSection> sections = new ArrayList<>();
        for (CourseSection section : courseSectionRepository.findByLecturerUserIdWithCourse(lecturerUserId)) {
            if (!section.getId().equals(target.getId())
                    && target.getAcademicTermId().equals(section.getAcademicTermId())) {
                sections.add(section);
            }
        }
        for (SectionComponent component : sectionComponentRepository.findByLecturerUserIdWithSection(lecturerUserId)) {
            CourseSection section = component.getSection();
            if (!section.getId().equals(target.getId())
                    && target.getAcademicTermId().equals(section.getAcademicTermId())
                    && sections.stream().noneMatch(existing -> existing.getId().equals(section.getId()))) {
                sections.add(section);
            }
        }
        return sections;
    }

    /**
     * Which of a section's meetings a lecturer already attached to it actually takes.
     *
     * <p>The whole section when they are its lecturer, otherwise only the sessions matching the
     * components they hold.
     */
    List<CourseCatalog.Meeting> meetingsTaughtBy(
            UUID lecturerUserId, CourseSection section, List<CourseCatalog.Meeting> meetings) {
        return restrictToWhatTheyTeach(lecturerUserId, section, meetings);
    }

    private List<CourseCatalog.Meeting> restrictToWhatTheyTeach(
            UUID lecturerUserId, CourseSection section, List<CourseCatalog.Meeting> meetings) {
        if (lecturerUserId.equals(section.getLecturerUserId())) {
            return meetings;
        }
        return filterToComponents(meetings, componentsTaughtBy(lecturerUserId, section.getId()));
    }

    private Set<CourseComponent> componentsTaughtBy(UUID lecturerUserId, UUID sectionId) {
        Set<CourseComponent> components = new LinkedHashSet<>();
        for (SectionComponent component : sectionComponentRepository.findBySectionId(sectionId)) {
            if (lecturerUserId.equals(component.getLecturerUserId())) {
                components.add(component.getComponent());
            }
        }
        return components;
    }

    private static List<CourseCatalog.Meeting> filterToComponents(
            List<CourseCatalog.Meeting> meetings, Set<CourseComponent> components) {
        if (components.isEmpty()) {
            return List.of();
        }
        List<CourseCatalog.Meeting> matching = new ArrayList<>();
        for (CourseCatalog.Meeting meeting : meetings) {
            if (components.stream().anyMatch(component -> matches(component, meeting.sessionType()))) {
                matching.add(meeting);
            }
        }
        return matching;
    }

    /**
     * {@code session_type} is free text while a component is an enum, so they are matched by name
     * rather than by identity. {@code LABORATORY} answers to "Lab" because that is what schedules
     * actually say.
     */
    private static boolean matches(CourseComponent component, String sessionType) {
        if (sessionType == null) {
            return false;
        }
        String value = sessionType.trim().toLowerCase(Locale.ROOT);
        return switch (component) {
            case LECTURE -> value.startsWith("lecture");
            case TUTORIAL -> value.startsWith("tutorial");
            case LABORATORY -> value.startsWith("lab");
        };
    }

    /**
     * G1: "Lab 3" and "Lab-3" used to be different rooms to this check, because the only
     * normalization was case and surrounding whitespace. Stripping space, hyphen, underscore and
     * period — the separators staff actually type — is what {@link SectionMeetingRepository
     * #findByTermAndRoom} applies to the stored value too, so both sides of the comparison use the
     * same key.
     */
    private static String normaliseRoom(String room) {
        if (room == null || room.isBlank()) {
            return null;
        }
        // Lower-cased and stripped here rather than in the query. A bare parameter inside a SQL
        // function is exactly the shape that once left Hibernate unable to infer a type and
        // produced `lower(bytea)` at runtime; comparing an already-normalized value avoids the
        // question entirely.
        return room.trim().toLowerCase(Locale.ROOT).replaceAll("[ \\-_.]", "");
    }

    private static List<CourseCatalog.Meeting> toMeetings(List<SectionMeeting> rows) {
        List<CourseCatalog.Meeting> meetings = new ArrayList<>();
        for (SectionMeeting row : rows) {
            meetings.add(toMeeting(row));
        }
        return meetings;
    }

    private static CourseCatalog.Meeting toMeeting(SectionMeeting row) {
        return new CourseCatalog.Meeting(
                row.getDayOfWeek(),
                com.university.lms.course.dto.SectionMeetingResponse.dayName(row.getDayOfWeek()),
                CLOCK.format(row.getStartTime()),
                CLOCK.format(row.getEndTime()),
                row.getRoom(),
                row.getSessionType());
    }
}
