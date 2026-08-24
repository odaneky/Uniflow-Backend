package com.university.lms.curriculum.repository;

import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the curriculum module. */
public interface ProgrammeRequirementBlockRepository extends JpaRepository<ProgrammeRequirementBlock, UUID> {

    List<ProgrammeRequirementBlock> findByCurriculumVersionIdOrderByPositionAsc(UUID curriculumVersionId);

    boolean existsByCurriculumVersionIdAndNameIgnoreCase(UUID curriculumVersionId, String name);
}
