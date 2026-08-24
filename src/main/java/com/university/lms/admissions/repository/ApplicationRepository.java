package com.university.lms.admissions.repository;

import com.university.lms.admissions.domain.Application;
import com.university.lms.admissions.domain.ApplicationStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByReference(String reference);

    /**
     * The resume lookup: reference AND the address it belongs to, never one alone.
     *
     * <p>Case-insensitive on both — a reference is read off a screen or an email and retyped, and
     * insisting on exact case would fail honest applicants far more often than it would stop anyone.
     */
    Optional<Application> findByReferenceIgnoreCaseAndApplicantEmailIgnoreCase(String reference, String applicantEmail);

    boolean existsByApplicantEmailIgnoreCaseAndProgrammeIdAndAcademicTermIdAndStatusNotIn(
            String applicantEmail, UUID programmeId, UUID academicTermId, Collection<ApplicationStatus> statuses);

    @Query(
            """
            SELECT a FROM Application a
            WHERE (:statuses IS NULL OR a.status IN :statuses)
              AND (:assignedTo IS NULL OR a.assignedTo = :assignedTo)
              AND (:referencePattern IS NULL OR LOWER(a.reference) LIKE :referencePattern ESCAPE '!')
            """)
    Page<Application> search(
            @Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("assignedTo") UUID assignedTo,
            @Param("referencePattern") String referencePattern,
            Pageable pageable);
}
