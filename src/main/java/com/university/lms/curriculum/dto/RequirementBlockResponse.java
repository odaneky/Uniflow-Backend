package com.university.lms.curriculum.dto;

import com.university.lms.curriculum.domain.ProgrammeRequirementBlock;
import com.university.lms.curriculum.domain.RequirementKind;
import java.util.List;
import java.util.UUID;

public record RequirementBlockResponse(
        UUID id,
        UUID programmeId,
        String name,
        RequirementKind kind,
        int requiredCredits,
        int position,
        List<UUID> courseIds) {

    public static RequirementBlockResponse from(ProgrammeRequirementBlock block) {
        return new RequirementBlockResponse(
                block.getId(),
                block.getProgrammeId(),
                block.getName(),
                block.getKind(),
                block.getRequiredCredits(),
                block.getPosition(),
                List.copyOf(block.getCourseIds()));
    }
}
