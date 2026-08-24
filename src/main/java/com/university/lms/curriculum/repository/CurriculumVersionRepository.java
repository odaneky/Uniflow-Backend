package com.university.lms.curriculum.repository;

import com.university.lms.curriculum.domain.CurriculumVersion;
import com.university.lms.curriculum.domain.CurriculumVersionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the curriculum module. */
public interface CurriculumVersionRepository extends JpaRepository<CurriculumVersion, UUID> {

    Optional<CurriculumVersion> findByProgrammeIdAndStatus(UUID programmeId, CurriculumVersionStatus status);

    long countByProgrammeId(UUID programmeId);
}
