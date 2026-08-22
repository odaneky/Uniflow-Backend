package com.university.lms.notification.service;

import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationPreference;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.dto.NotificationPreferenceResponse;
import com.university.lms.notification.dto.UpdateNotificationPreferencesRequest;
import com.university.lms.notification.repository.NotificationPreferenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /** Missing rows default to enabled. */
    public boolean isEnabled(UUID userId, NotificationType type, NotificationChannel channel) {
        return repository
                .findByUserIdAndNotificationTypeAndChannel(userId, type, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    public List<NotificationPreferenceResponse> listForUser(UUID userId) {
        Map<String, NotificationPreference> stored = repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> key(row.getNotificationType(), row.getChannel()), Function.identity()));

        List<NotificationPreferenceResponse> result = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            for (NotificationChannel channel : List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)) {
                NotificationPreference row = stored.get(key(type, channel));
                boolean enabled = row == null || row.isEnabled();
                result.add(new NotificationPreferenceResponse(type, channel, enabled));
            }
        }
        return result;
    }

    @Transactional
    public List<NotificationPreferenceResponse> update(UUID userId, UpdateNotificationPreferencesRequest request) {
        for (UpdateNotificationPreferencesRequest.PreferenceItem item : request.preferences()) {
            NotificationPreference row = repository
                    .findByUserIdAndNotificationTypeAndChannel(userId, item.notificationType(), item.channel())
                    .orElseGet(() -> new NotificationPreference(userId, item.notificationType(), item.channel(), true));
            row.setEnabled(item.enabled());
            repository.save(row);
        }
        return listForUser(userId);
    }

    private static String key(NotificationType type, NotificationChannel channel) {
        return type.name() + ":" + channel.name();
    }
}
