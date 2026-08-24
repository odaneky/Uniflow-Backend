package com.university.lms.staffing.dto;

import com.university.lms.staffing.domain.OrgUnit;
import com.university.lms.staffing.domain.OrgUnitType;
import java.util.UUID;

public record OrgUnitResponse(UUID id, UUID parentId, String code, String name, OrgUnitType unitType) {

    public static OrgUnitResponse from(OrgUnit unit) {
        return new OrgUnitResponse(
                unit.getId(),
                unit.getParent() == null ? null : unit.getParent().getId(),
                unit.getCode(),
                unit.getName(),
                unit.getUnitType());
    }
}
