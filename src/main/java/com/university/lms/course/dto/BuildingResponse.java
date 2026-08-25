package com.university.lms.course.dto;

import com.university.lms.course.domain.Building;
import java.util.UUID;

public record BuildingResponse(UUID id, String code, String name) {

    public static BuildingResponse from(Building building) {
        return new BuildingResponse(building.getId(), building.getCode(), building.getName());
    }
}
