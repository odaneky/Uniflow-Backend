package com.university.lms.curriculum.service;

import com.university.lms.curriculum.api.CurriculumCatalog;
import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import com.university.lms.curriculum.repository.ProgrammeRequirementBlockRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts requirement blocks to the published {@link CurriculumCatalog} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultCurriculumCatalog implements CurriculumCatalog {

    private final ProgrammeRequirementBlockRepository blockRepository;

    public DefaultCurriculumCatalog(ProgrammeRequirementBlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Override
    public boolean allowsEnrolment(UUID programmeId, UUID courseId) {
        if (programmeId == null || courseId == null) {
            return true;
        }
        List<ProgrammeRequirementBlock> blocks = blockRepository.findByProgrammeIdOrderByPositionAsc(programmeId);
        if (blocks.isEmpty()) {
            return true;
        }
        for (ProgrammeRequirementBlock block : blocks) {
            if (block.getKind() == RequirementKind.FREE_ELECTIVE) {
                return true;
            }
        }
        for (ProgrammeRequirementBlock block : blocks) {
            if (block.getCourseIds().contains(courseId)) {
                return true;
            }
        }
        return false;
    }
}
