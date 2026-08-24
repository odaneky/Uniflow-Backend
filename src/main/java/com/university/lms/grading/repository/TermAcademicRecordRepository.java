package com.university.lms.grading.repository;

import com.university.lms.grading.domain.TermAcademicRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the grading module. */
public interface TermAcademicRecordRepository extends JpaRepository<TermAcademicRecord, UUID> {

    boolean existsByStudentIdAndAcademicTermId(UUID studentId, UUID academicTermId);

    List<TermAcademicRecord> findByStudentIdOrderByTermOrderAsc(UUID studentId);

    List<TermAcademicRecord> findByAcademicTermId(UUID academicTermId);

    Optional<TermAcademicRecord> findTopByStudentIdOrderByTermOrderDesc(UUID studentId);
}
