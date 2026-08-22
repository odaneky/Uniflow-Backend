package com.university.lms.course.repository;

import com.university.lms.course.domain.CourseSection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the course module — other modules use {@code course.api.CourseCatalog}. */
public interface CourseSectionRepository extends JpaRepository<CourseSection, UUID> {

    List<CourseSection> findByCourseId(UUID courseId);

    List<CourseSection> findByCourseIdAndAcademicTermId(UUID courseId, UUID academicTermId);

    List<CourseSection> findByAcademicTermId(UUID academicTermId);

    boolean existsByCourseIdAndSectionCodeIgnoreCase(UUID courseId, String sectionCode);

    /** Fetch-joins the course so a section read does not trigger a second query for its parent. */
    @Query("select cs from CourseSection cs join fetch cs.course where cs.id = :id")
    Optional<CourseSection> findByIdWithCourse(@Param("id") UUID id);

    @Query("select cs from CourseSection cs join fetch cs.course where cs.lecturerUserId = :lecturerUserId")
    List<CourseSection> findByLecturerUserIdWithCourse(@Param("lecturerUserId") UUID lecturerUserId);

    @Query("select cs from CourseSection cs join fetch cs.course where cs.lecturerUserId is not null")
    List<CourseSection> findAssignedWithCourse();

    /**
     * Atomically takes one seat, or reports that none was available.
     *
     * <p>This is the heart of the concurrency strategy. The capacity check and the increment happen
     * inside a single UPDATE, so the database evaluates the predicate while holding the row lock
     * it needs for the write — two simultaneous callers are serialised by the engine, and exactly
     * one of them sees a row count of 1. The read-then-write alternative
     * ({@code if (hasAvailableSeats()) { increment(); }}) is a textbook lost update and would
     * over-fill a section under load.
     *
     * <p>{@code version} is incremented explicitly. Without it, an entity instance loaded before
     * this statement could later be flushed and — because Hibernate writes every column on a
     * dirty update — silently restore its stale {@code enrolled_count}, undoing a reservation.
     * Bumping the version makes that flush fail its optimistic-lock check instead.
     *
     * <p><b>Why native SQL rather than HQL.</b> The natural HQL form is
     * {@code update versioned CourseSection ...}, which asks Hibernate to add the version
     * assignment itself. That is not thread-safe in Hibernate 6.5: {@code addVersionedAssignment}
     * mutates the SQM tree during translation, and that tree is cached and shared between threads,
     * so two callers translating it at once throw {@link java.util.ConcurrentModificationException}
     * from inside Hibernate. Under a registration rush that surfaced as sporadic 500s — roughly
     * one request in forty in {@code ConcurrentEnrollmentIntegrationTest}. Native SQL skips SQM
     * translation altogether, so the problem cannot arise, and the statement is one we want to
     * control precisely in any case.
     *
     * @return 1 when a seat was taken, 0 when the section is full, not open, or absent
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    update course_sections
                       set enrolled_count = enrolled_count + 1,
                           version        = version + 1
                     where id = :sectionId
                       and status = :openStatus
                       and enrolled_count < capacity
                    """,
            nativeQuery = true)
    int reserveSeat(@Param("sectionId") UUID sectionId, @Param("openStatus") String openStatus);

    /**
     * Returns a seat when an enrolment is dropped. Guarded so the counter can never go negative,
     * even if a release were somehow issued twice.
     *
     * <p>Native for the same reason as {@link #reserveSeat}.
     *
     * @return 1 when a seat was returned, 0 when the counter was already at zero
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    update course_sections
                       set enrolled_count = enrolled_count - 1,
                           version        = version + 1
                     where id = :sectionId
                       and enrolled_count > 0
                    """,
            nativeQuery = true)
    int releaseSeat(@Param("sectionId") UUID sectionId);

    /**
     * Sets the denormalized counter exactly. Used when enrolment reconciles after detecting drift.
     * Version is bumped so a stale entity flush cannot silently undo the repair.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    update course_sections
                       set enrolled_count = :occupyingSeats,
                           version        = version + 1
                     where id = :sectionId
                       and :occupyingSeats >= 0
                       and :occupyingSeats <= capacity
                    """,
            nativeQuery = true)
    int replaceEnrolledCount(
            @Param("sectionId") UUID sectionId, @Param("occupyingSeats") int occupyingSeats);
}
