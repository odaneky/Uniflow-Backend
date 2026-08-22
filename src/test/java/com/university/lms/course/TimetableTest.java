package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.course.api.CourseCatalog.Meeting;
import com.university.lms.course.api.Timetable;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimetableTest {

    @Test
    @DisplayName("sessions on the same day overlap when their clocks intersect")
    void overlappingClocksClash() {
        Meeting lecture = meeting(1, "09:00", "10:00", "Lecture");
        Meeting tutorial = meeting(1, "09:30", "10:30", "Tutorial");

        assertThat(Timetable.overlaps(lecture, tutorial)).isTrue();
        assertThat(Timetable.clashes(List.of(lecture), List.of(tutorial))).isTrue();
    }

    @Test
    @DisplayName("back-to-back sessions on the same day do not clash")
    void adjacentSessionsDoNotClash() {
        Meeting morning = meeting(1, "09:00", "10:00", "Lecture");
        Meeting next = meeting(1, "10:00", "11:00", "Tutorial");

        assertThat(Timetable.overlaps(morning, next)).isFalse();
    }

    @Test
    @DisplayName("the same clock on different days does not clash")
    void differentDaysDoNotClash() {
        Meeting monday = meeting(1, "09:00", "10:00", "Lecture");
        Meeting tuesday = meeting(2, "09:00", "10:00", "Tutorial");

        assertThat(Timetable.overlaps(monday, tuesday)).isFalse();
    }

    @Test
    @DisplayName("a Monday lecture may be followed by a Thursday tutorial")
    void lectureBeforeTutorialIsLegal() {
        List<Meeting> occurrence = List.of(
                meeting(1, "09:00", "10:00", "Lecture"), meeting(4, "09:00", "10:00", "Tutorial"));

        assertThat(Timetable.componentOrderIssue(List.of(occurrence))).isEmpty();
    }

    @Test
    @DisplayName("a Thursday lecture may be followed by a Monday tutorial of the next week")
    void lateLectureMayWrapToEarlyTutorial() {
        List<Meeting> occurrence = List.of(
                meeting(4, "09:00", "10:00", "Lecture"), meeting(1, "09:00", "10:00", "Tutorial"));

        assertThat(Timetable.componentOrderIssue(List.of(occurrence))).isEmpty();
    }

    @Test
    @DisplayName("a Wednesday lecture cannot be followed by a Tuesday tutorial the same week")
    void tutorialBeforeLectureIsRefused() {
        List<Meeting> occurrence = List.of(
                meeting(3, "09:00", "10:00", "Lecture"), meeting(2, "09:00", "10:00", "Tutorial"));

        assertThat(Timetable.componentOrderIssue(List.of(occurrence)))
                .contains(
                        "Every Lecture section must start before every Tutorial section that week (or run late enough in the week that the tutorial can follow early the next week).");
    }

    @Test
    @DisplayName("order is judged across streams of the same course, not only inside one occurrence")
    void latestLectureMustStillPrecedeTutorials() {
        List<Meeting> lectureStream = List.of(meeting(5, "09:00", "10:00", "Lecture"));
        List<Meeting> tutorialStream = List.of(meeting(4, "09:00", "10:00", "Tutorial"));

        assertThat(Timetable.componentOrderIssue(List.of(lectureStream, tutorialStream))).isPresent();
    }

    private static Meeting meeting(int day, String start, String end, String type) {
        return new Meeting(day, "Day", start, end, "A1", type);
    }
}
