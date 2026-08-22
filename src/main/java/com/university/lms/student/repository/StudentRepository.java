package com.university.lms.student.repository;

import com.university.lms.student.domain.Student;
import com.university.lms.student.domain.StudentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Internal to the student module — other modules use {@code student.api.StudentDirectory}. */
public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByStudentNumber(String studentNumber);

    boolean existsByStudentNumber(String studentNumber);

    boolean existsByUserId(UUID userId);

    /** Resolves an authenticated user to their student record; backed by the unique index on user_id. */
    Optional<Student> findByUserId(UUID userId);

    Page<Student> findByAdvisorUserId(UUID advisorUserId, Pageable pageable);

    /**
     * Single filtered, paged query rather than several finder overloads. Null arguments disable
     * their predicate, so the caller never has to choose between combinations of finders and the
     * database always receives one statement with a bounded result set.
     */
    @Query(
            """
            select s from Student s
            where (:status is null or s.status = :status)
              and (:programmeId is null or s.programmeId = :programmeId)
              and (:advisorUserId is null or s.advisorUserId = :advisorUserId)
            """)
    Page<Student> search(
            StudentStatus status, UUID programmeId, UUID advisorUserId, Pageable pageable);
}
