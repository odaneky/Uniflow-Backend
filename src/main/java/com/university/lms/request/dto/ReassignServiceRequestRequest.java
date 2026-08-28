package com.university.lms.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** D9: hands a request off to a different staff member than the one currently assigned. */
public record ReassignServiceRequestRequest(
        @NotNull(message = "is required") UUID toUserId,
        @Size(max = 500, message = "must be at most 500 characters") String note) {}
