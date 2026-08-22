package com.university.lms.admissions.repository;

import com.university.lms.admissions.domain.ApplicationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, UUID> {

    List<ApplicationEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
