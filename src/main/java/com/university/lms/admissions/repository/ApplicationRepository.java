package com.university.lms.admissions.repository;

import com.university.lms.admissions.domain.Application;
import com.university.lms.admissions.domain.ApplicationStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByReference(String reference);

    boolean existsByApplicantEmailIgnoreCaseAndProgrammeIdAndAcademicTermIdAndStatusNotIn(
            String applicantEmail, UUID programmeId, UUID academicTermId, Collection<ApplicationStatus> statuses);

    @Query(
            """
            SELECT a FROM Application a
            WHERE (:statuses IS NULL OR a.status IN :statuses)
              AND (:assignedTo IS NULL OR a.assignedTo = :assignedTo)
              AND (:reference IS NULL OR LOWER(a.reference) LIKE LOWER(CONCAT('%', :reference, '%')))
            """)
    Page<Application> search(
            @Param("statuses") Collection<ApplicationStatus> statuses,
            @Param("assignedTo") UUID assignedTo,
            @Param("reference") String reference,
            Pageable pageable);
}
