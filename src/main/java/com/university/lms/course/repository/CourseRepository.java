package com.university.lms.course.repository;

import com.university.lms.course.domain.Course;
import com.university.lms.course.domain.CourseStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the course module — other modules use {@code course.api.CourseCatalog}. */
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCourseCodeIgnoreCase(String courseCode);

    boolean existsByCourseCodeIgnoreCase(String courseCode);

    /**
     * One filtered, paged statement; null arguments disable their predicate.
     *
     * <p>{@code searchPattern} is a complete, already-lowercased LIKE pattern built by
     * {@code CourseService}, not a bare term. That is deliberate and load-bearing.
     *
     * <p>The obvious formulation — {@code like lower(concat('%', :search, '%'))} — is broken on
     * PostgreSQL. Neither {@code :search is null} nor HQL's variadic {@code concat} constrains the
     * parameter's type, so Hibernate cannot infer one and falls back to its serializable mapping,
     * binding the value as {@code bytea}. PostgreSQL then looks for {@code lower(bytea)}, finds no
     * such function, and every unfiltered listing fails with SQLState 42883. Passing the pattern
     * whole puts the parameter directly beside {@code like}, whose right operand is typed as text,
     * so the binding is unambiguous.
     *
     * <p>It is also cheaper: the pattern is built once per query rather than concatenated and
     * lowercased per row.
     *
     * <p>{@code escape '!'} makes {@code %} and {@code _} inside a user's search term literal, so
     * searching for "50%" does not silently match every course.
     */
    @Query(
            """
            select c from Course c
            where (:status is null or c.status = :status)
              and (:departmentId is null or c.departmentId = :departmentId)
              and (:searchPattern is null
                   or lower(c.title) like :searchPattern escape '!'
                   or lower(c.courseCode) like :searchPattern escape '!')
            """)
    Page<Course> search(
            @Param("status") CourseStatus status,
            @Param("departmentId") UUID departmentId,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);
}
