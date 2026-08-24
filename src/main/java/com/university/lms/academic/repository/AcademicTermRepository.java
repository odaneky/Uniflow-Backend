package com.university.lms.academic.repository;

import com.university.lms.academic.domain.AcademicTerm;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the academic module — cross-module reads go through {@code academic.api}. */
public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {

    List<AcademicTerm> findByAcademicYearIdOrderBySequenceNumberAsc(UUID academicYearId);

    boolean existsByAcademicYearIdAndSequenceNumber(UUID academicYearId, int sequenceNumber);

    /** Count of terms at or before this (start_date, sequence_number) — the term's 1-based ordinal. */
    @Query(
            "SELECT COUNT(t) FROM AcademicTerm t "
                    + "WHERE t.startDate < :startDate "
                    + "OR (t.startDate = :startDate AND t.sequenceNumber <= :sequenceNumber)")
    long countUpToAndIncluding(@Param("startDate") LocalDate startDate, @Param("sequenceNumber") int sequenceNumber);
}
