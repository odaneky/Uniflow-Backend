package com.university.lms.staffing.dto;

import com.university.lms.staffing.domain.OrgUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOrgUnitRequest(
        UUID parentId,
        @NotBlank(message = "is required") @Size(max = 30, message = "must be at most 30 characters") String code,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String name,
        @NotNull(message = "is required") OrgUnitType unitType) {}
