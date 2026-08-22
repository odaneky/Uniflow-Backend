package com.university.lms.request.repository;

import com.university.lms.request.domain.ServiceRequestEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestEventRepository extends JpaRepository<ServiceRequestEvent, UUID> {

    List<ServiceRequestEvent> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
