package com.university.lms.request.dto;

import com.university.lms.request.domain.ServiceRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateServiceRequestRequest(
        @NotNull(message = "is required") ServiceRequestType type,
        @Size(max = 2000, message = "must be at most 2000 characters") String note) {}
