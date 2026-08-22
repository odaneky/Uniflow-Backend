package com.university.lms.administration.repository;

import com.university.lms.administration.domain.RecordAccessEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordAccessEventRepository extends JpaRepository<RecordAccessEvent, UUID> {

    Page<RecordAccessEvent> findByStudentIdOrderByAccessedAtDesc(UUID studentId, Pageable pageable);
}
