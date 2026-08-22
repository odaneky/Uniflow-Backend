package com.university.lms.learning.dto;

import com.university.lms.learning.domain.LearningMaterial;
import com.university.lms.learning.domain.MaterialType;
import java.util.UUID;

public record LearningMaterialResponse(
        UUID id,
        String title,
        MaterialType materialType,
        String externalUrl,
        UUID documentId,
        int position) {

    public static LearningMaterialResponse from(LearningMaterial material) {
        return new LearningMaterialResponse(
                material.getId(),
                material.getTitle(),
                material.getMaterialType(),
                material.getExternalUrl(),
                material.getDocumentId(),
                material.getPosition());
    }
}
