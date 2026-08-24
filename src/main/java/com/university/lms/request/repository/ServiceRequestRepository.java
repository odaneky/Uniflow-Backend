package com.university.lms.request.repository;

import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the request module. */
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    List<ServiceRequest> findByStudentIdOrderByUpdatedAtDesc(UUID studentId);

    boolean existsByStudentIdAndRequestTypeAndStatusNotIn(
            UUID studentId, ServiceRequestType requestType, Collection<ServiceRequestStatus> statuses);

    boolean existsByReference(String reference);

    /** Mirrors uk_service_requests_open_appeal_per_grade: one open appeal per grade, not per student. */
    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM service_requests "
                    + "WHERE request_type = 'APPEAL' AND status NOT IN ('COMPLETED', 'DENIED', 'CANCELLED') "
                    + "AND payload ->> 'gradeId' = :gradeId)",
            nativeQuery = true)
    boolean existsOpenAppealForGrade(@Param("gradeId") String gradeId);

    @Query(
            """
            SELECT r FROM ServiceRequest r
            WHERE (:statuses IS NULL OR r.status IN :statuses)
              AND (:type IS NULL OR r.requestType = :type)
              AND (:assignedTo IS NULL OR r.assignedTo = :assignedTo)
              AND (:referencePattern IS NULL OR LOWER(r.reference) LIKE :referencePattern ESCAPE '!')
            """)
    Page<ServiceRequest> search(
            @Param("statuses") Collection<ServiceRequestStatus> statuses,
            @Param("type") ServiceRequestType type,
            @Param("assignedTo") UUID assignedTo,
            @Param("referencePattern") String referencePattern,
            Pageable pageable);
}
