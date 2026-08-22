package com.university.lms.branding.repository;

import com.university.lms.branding.domain.InstitutionBranding;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionBrandingRepository extends JpaRepository<InstitutionBranding, UUID> {}
