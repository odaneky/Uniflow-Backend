package com.university.lms.request.repository;

import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the request module. */
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    List<ServiceRequest> findByStudentIdOrderByUpdatedAtDesc(UUID studentId);

    boolean existsByStudentIdAndRequestTypeAndStatusNotIn(
            UUID studentId, ServiceRequestType requestType, Collection<ServiceRequestStatus> statuses);

    boolean existsByReference(String reference);
}
