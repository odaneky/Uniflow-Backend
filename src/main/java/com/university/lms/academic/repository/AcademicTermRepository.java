package com.university.lms.academic.repository;

import com.university.lms.academic.domain.AcademicTerm;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the academic module — cross-module reads go through {@code academic.api}. */
public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {

    List<AcademicTerm> findByAcademicYearIdOrderBySequenceNumberAsc(UUID academicYearId);

    boolean existsByAcademicYearIdAndSequenceNumber(UUID academicYearId, int sequenceNumber);
}
