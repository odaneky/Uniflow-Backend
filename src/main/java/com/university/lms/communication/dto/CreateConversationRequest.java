package com.university.lms.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateConversationRequest(
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String subject,
        @NotEmpty(message = "is required") List<UUID> participantUserIds,
        UUID courseSectionId,
        @Size(max = 4000, message = "must be at most 4000 characters") String firstMessage) {}
