package com.university.lms.notification.dto;

import com.university.lms.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateNotificationRequest(
        @NotNull(message = "is required") UUID recipientUserId,
        @NotNull(message = "is required") NotificationType notificationType,
        @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String title,
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters") String body,
        @Size(max = 500, message = "must be at most 500 characters") String actionUrl) {}
