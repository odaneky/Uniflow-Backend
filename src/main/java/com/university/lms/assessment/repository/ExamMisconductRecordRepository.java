package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.ExamMisconductRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamMisconductRecordRepository extends JpaRepository<ExamMisconductRecord, UUID> {

    List<ExamMisconductRecord> findByExamSittingIdOrderByCreatedAtDesc(UUID examSittingId);
}
