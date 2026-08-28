package com.university.lms.financialaid.repository;

import com.university.lms.financialaid.domain.ScholarshipProgramme;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScholarshipProgrammeRepository extends JpaRepository<ScholarshipProgramme, UUID> {

    List<ScholarshipProgramme> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
