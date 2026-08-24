package com.university.lms.admissions.repository;

import com.university.lms.admissions.domain.ProgrammeApplicationForm;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeApplicationFormRepository extends JpaRepository<ProgrammeApplicationForm, UUID> {}
