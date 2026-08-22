package com.university.lms.request.dto;

import com.university.lms.request.domain.ServiceRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideServiceRequestRequest(
        @NotNull(message = "is required") ServiceRequestStatus status,
        @Size(max = 2000, message = "must be at most 2000 characters") String note) {}
