package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.common.exception.ApplicationException;
import com.university.lms.course.domain.CourseComponent;
import com.university.lms.course.domain.CourseErrorCode;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.domain.SectionComponent;
import com.university.lms.course.dto.AssignLecturerRequest;
import com.university.lms.course.dto.ReplaceSectionMeetingsRequest;
import com.university.lms.course.repository.SectionComponentRepository;
import com.university.lms.course.service.CourseService;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Nobody teaches two classes at once, and no room holds two.
 *
 * <p>The timetable already refused sessions that clashed <em>within</em> an occurrence, and refused
 * a student two overlapping enrolments. Neither said anything about the person at the front or the
 * room they were standing in, so the same lecturer could be booked into two nine-o'clock classes.
 */
class TeachingConflictIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private AcademicFixtures fixtures;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SectionComponentRepository sectionComponentRepository;

    private UUID lecturer() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return userRepository
                .saveAndFlush(new User("lec-" + tag, "lec-" + tag + "@university.test", "Lee", "Turner"))
                .getId();
    }

    private static ReplaceSectionMeetingsRequest meetingsAt(
            int day, String start, String end, String room, String type) {
        return new ReplaceSectionMeetingsRequest(
                List.of(new ReplaceSectionMeetingsRequest.MeetingRequest(
                        day, LocalTime.parse(start), LocalTime.parse(end), room, type)),
                null);
    }

    /** Two sections of different courses in one term — the normal timetabling situation. */
    private CourseSection sectionInSameTermAs(AcademicTerm term) {
        return fixtures.openSection(term, 30);
    }

    @Nested
    @DisplayName("Lecturer")
    class Lecturer {

        @Test
        @DisplayName("cannot be assigned to two classes at the same time")
        void refusesDoubleBooking() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);
            UUID teacher = lecturer();

            courseService.replaceMeetings(first.getId(), meetingsAt(1, "09:00", "10:00", "LT-1", "Lecture"));
            courseService.replaceMeetings(second.getId(), meetingsAt(1, "09:00", "10:00", "LT-2", "Lecture"));

            courseService.assignLecturer(first.getId(), teacher);

            assertThatThrownBy(() -> courseService.assignLecturer(second.getId(), teacher))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                    .isEqualTo(CourseErrorCode.SCHEDULE_CONFLICT);
        }

        @Test
        @DisplayName("may teach two classes that do not overlap")
        void allowsNonOverlapping() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);
            UUID teacher = lecturer();

            courseService.replaceMeetings(first.getId(), meetingsAt(1, "09:00", "10:00", "LT-1", "Lecture"));
            courseService.replaceMeetings(second.getId(), meetingsAt(1, "11:00", "12:00", "LT-2", "Lecture"));

            courseService.assignLecturer(first.getId(), teacher);
            assertThat(courseService.assignLecturer(second.getId(), teacher)).isNotNull();
        }

        /**
         * The bypass the second call site exists to close: assign while the timetable is clear, then
         * move the sessions on top of the other class.
         */
        @Test
        @DisplayName("cannot be moved into a clash after being assigned cleanly")
        void refusesMovingMeetingsIntoAClash() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);
            UUID teacher = lecturer();

            courseService.replaceMeetings(first.getId(), meetingsAt(1, "09:00", "10:00", "LT-1", "Lecture"));
            courseService.replaceMeetings(second.getId(), meetingsAt(2, "09:00", "10:00", "LT-2", "Lecture"));
            courseService.assignLecturer(first.getId(), teacher);
            courseService.assignLecturer(second.getId(), teacher);

            assertThatThrownBy(() -> courseService.replaceMeetings(
                            second.getId(), meetingsAt(1, "09:00", "10:00", "LT-2", "Lecture")))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                    .isEqualTo(CourseErrorCode.SCHEDULE_CONFLICT);
        }

        /**
         * A tutor attached to the tutorial component teaches only the tutorial meetings. Comparing
         * them against the lecture would report a clash with a class they do not take.
         */
        @Test
        @DisplayName("attached to one component clashes only on that component's sessions")
        void componentLecturerIsScopedToTheirSessions() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection taught = sectionInSameTermAs(term);
            CourseSection other = sectionInSameTermAs(term);
            UUID tutor = lecturer();

            // Monday 09:00 lecture, Monday 14:00 tutorial.
            courseService.replaceMeetings(
                    taught.getId(),
                    new ReplaceSectionMeetingsRequest(
                            List.of(
                                    new ReplaceSectionMeetingsRequest.MeetingRequest(
                                            1, LocalTime.parse("09:00"), LocalTime.parse("10:00"), "LT-1", "Lecture"),
                                    new ReplaceSectionMeetingsRequest.MeetingRequest(
                                            1, LocalTime.parse("14:00"), LocalTime.parse("15:00"), "LAB-1", "Tutorial")),
                            null));
            sectionComponentRepository.saveAndFlush(
                    new SectionComponent(taught, CourseComponent.TUTORIAL, 25, tutor));

            // Another class at 09:00 — the same slot as the LECTURE they do not teach.
            courseService.replaceMeetings(other.getId(), meetingsAt(1, "09:00", "10:00", "LT-9", "Lecture"));

            assertThat(courseService.assignLecturer(other.getId(), tutor))
                    .as("the tutor is free at 09:00; only their 14:00 tutorial is committed")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Room")
    class Room {

        @Test
        @DisplayName("cannot hold two classes at once")
        void refusesDoubleBookedRoom() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);

            courseService.replaceMeetings(first.getId(), meetingsAt(3, "13:00", "14:00", "SR-201", "Lecture"));

            assertThatThrownBy(() -> courseService.replaceMeetings(
                            second.getId(), meetingsAt(3, "13:30", "14:30", "SR-201", "Lecture")))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                    .isEqualTo(CourseErrorCode.SCHEDULE_CONFLICT);
        }

        /** Rooms are free text, so "sr-201 " and "SR-201" have to be the same room. */
        @Test
        @DisplayName("is matched regardless of case or stray spaces")
        void matchesRoomsLoosely() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);

            courseService.replaceMeetings(first.getId(), meetingsAt(4, "10:00", "11:00", "SR-303", "Lecture"));

            assertThatThrownBy(() -> courseService.replaceMeetings(
                            second.getId(), meetingsAt(4, "10:00", "11:00", " sr-303 ", "Lecture")))
                    .isInstanceOf(ApplicationException.class);
        }

        @Test
        @DisplayName("may be reused at a different time")
        void allowsSequentialBookings() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);

            courseService.replaceMeetings(first.getId(), meetingsAt(5, "09:00", "10:00", "SR-404", "Lecture"));
            assertThat(courseService.replaceMeetings(
                            second.getId(), meetingsAt(5, "10:00", "11:00", "SR-404", "Lecture")))
                    .isNotNull();
        }

        /** Editing a section must not find itself already in the room it is already in. */
        @Test
        @DisplayName("does not clash with the section being edited")
        void ignoresTheSectionBeingEdited() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection section = sectionInSameTermAs(term);

            courseService.replaceMeetings(section.getId(), meetingsAt(2, "15:00", "16:00", "SR-505", "Lecture"));
            assertThat(courseService.replaceMeetings(
                            section.getId(), meetingsAt(2, "15:00", "16:00", "SR-505", "Lecture")))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Override")
    class Override {

        @Test
        @DisplayName("lets timetabling schedule a clash deliberately")
        void allowsExplicitOverride() {
            AcademicTerm term = fixtures.openTerm();
            CourseSection first = sectionInSameTermAs(term);
            CourseSection second = sectionInSameTermAs(term);
            UUID teacher = lecturer();

            courseService.replaceMeetings(first.getId(), meetingsAt(1, "09:00", "10:00", "LT-1", "Lecture"));
            courseService.replaceMeetings(second.getId(), meetingsAt(1, "09:00", "10:00", "LT-2", "Lecture"));
            courseService.assignLecturer(first.getId(), teacher);

            assertThat(courseService.assignLecturer(second.getId(), teacher, true))
                    .as("a hard block with no escape hatch gets worked around by entering wrong data")
                    .isNotNull();
        }
    }
}
