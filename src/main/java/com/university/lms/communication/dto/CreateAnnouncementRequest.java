package com.university.lms.communication.dto;

import com.university.lms.communication.domain.AnnouncementAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAnnouncementRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        @NotBlank(message = "is required") @Size(max = 4000, message = "must be at most 4000 characters") String body,
        @NotNull(message = "is required") AnnouncementAudience audience,
        UUID audienceRefId,
        Boolean publish) {}
