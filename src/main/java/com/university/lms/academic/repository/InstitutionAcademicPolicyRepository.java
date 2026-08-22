package com.university.lms.academic.repository;

import com.university.lms.academic.domain.InstitutionAcademicPolicy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionAcademicPolicyRepository extends JpaRepository<InstitutionAcademicPolicy, UUID> {}
