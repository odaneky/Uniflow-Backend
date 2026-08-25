package com.university.lms.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBuildingRequest(
        @NotBlank(message = "is required") @Size(max = 20, message = "must be at most 20 characters") String code,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String name) {}
