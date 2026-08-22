package com.university.lms.course.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The course module's published contract.
 *
 * <p>Seat management is exposed as behaviour, not as data. The enrolment module asks this module
 * to {@link #tryReserveSeat} and is told yes or no; it never reads a capacity, forms its own
 * opinion, and writes a counter back. Keeping the decision inside the module that owns the row is
 * what makes the operation safe under concurrency, and is what would let this module move behind a
 * network boundary without the rule changing.
 */
public interface CourseCatalog {

    record CourseSummary(UUID id, String courseCode, String title, int credits, int level, boolean offerable) {}

    record SectionSummary(
            UUID id,
            UUID courseId,
            String courseCode,
            UUID academicTermId,
            String sectionCode,
            int capacity,
            int enrolledCount,
            boolean openForEnrolment,
            UUID lecturerUserId) {}

    /** Sections this user is assigned to teach. Empty when they teach none. */
    List<SectionSummary> findSectionsTaughtBy(UUID lecturerUserId);

    /**
     * Whether this user is the assigned lecturer of the section.
     *
     * <p>False when the section is unknown, has no lecturer, or is assigned to someone else. Callers
     * must not treat {@code false} as "no such section" — look up the section if they need a 404.
     */
    boolean teaches(UUID lecturerUserId, UUID sectionId);

    /**
     * One scheduled meeting of a section. {@code dayOfWeek} is ISO (1 = Monday … 5 = Friday).
     * Times are wall-clock in the campus timezone, not instants.
     */
    record Meeting(
            int dayOfWeek, String day, String startTime, String endTime, String room, String sessionType) {}

    List<Meeting> meetingsOf(UUID sectionId);

    boolean courseExists(UUID courseId);

    Optional<CourseSummary> findCourse(UUID courseId);

    Optional<SectionSummary> findSection(UUID sectionId);

    /**
     * Clauses the student must satisfy to enrol in this course. Empty when there are none.
     *
     * <p>Groups are ANDed. {@code anyOf} inside a group is OR. {@code MINIMUM_LEVEL} uses
     * {@code minimumLevel} and an empty {@code anyOf}.
     */
    record RequirementClause(String kind, Integer minimumLevel, List<CourseSummary> anyOf) {}

    List<RequirementClause> requirementsOf(UUID courseId);

    /**
     * Human-readable unmet clauses, given the caller's completed and in-progress catalog courses.
     * Empty means the student may enrol as far as this module is concerned.
     */
    List<String> unmetRequirements(
            UUID courseId, Set<UUID> completedCourseIds, Set<UUID> inProgressCourseIds, int highestCompletedLevel);

    /**
     * Attempts to take one seat in the section.
     *
     * <p>Must be called inside the caller's transaction so that taking the seat and recording the
     * enrolment commit or roll back together.
     *
     * @return true when a seat was secured; false when the section is full, not open, or unknown
     */
    boolean tryReserveSeat(UUID sectionId);

    /** Returns a previously taken seat. Safe to call when the counter is already zero. */
    void releaseSeat(UUID sectionId);

    /**
     * Overwrites the denormalized seat counter to match occupying enrolments.
     *
     * <p>Enrolment owns who is on the roster; this module owns the counter used for capacity.
     * When the two drift (failed release, legacy demo data), enrolment asks course to realign the
     * counter rather than writing {@code course_sections} itself.
     */
    void replaceEnrolledCount(UUID sectionId, int occupyingSeats);
}
