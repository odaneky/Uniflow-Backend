package com.university.lms.notification.dto;

import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationType;

public record NotificationPreferenceResponse(
        NotificationType notificationType, NotificationChannel channel, boolean enabled) {}
