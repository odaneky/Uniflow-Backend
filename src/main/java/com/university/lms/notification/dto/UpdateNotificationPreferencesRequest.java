package com.university.lms.notification.dto;

import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateNotificationPreferencesRequest(@Valid @NotNull List<PreferenceItem> preferences) {

    public record PreferenceItem(
            @NotNull NotificationType notificationType,
            @NotNull NotificationChannel channel,
            boolean enabled) {}
}
