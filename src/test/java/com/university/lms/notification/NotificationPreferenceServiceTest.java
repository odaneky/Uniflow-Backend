package com.university.lms.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.notification.domain.NotificationChannel;
import com.university.lms.notification.domain.NotificationPreference;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.repository.NotificationPreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository);
    }

    @Test
    void missingPreferenceDefaultsToEnabled() {
        when(repository.findByUserIdAndNotificationTypeAndChannel(
                        userId, NotificationType.MESSAGE, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());

        assertThat(service.isEnabled(userId, NotificationType.MESSAGE, NotificationChannel.IN_APP))
                .isTrue();
    }

    @Test
    void storedDisabledPreferenceIsRespected() {
        when(repository.findByUserIdAndNotificationTypeAndChannel(
                        userId, NotificationType.MESSAGE, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(new NotificationPreference(
                        userId, NotificationType.MESSAGE, NotificationChannel.EMAIL, false)));

        assertThat(service.isEnabled(userId, NotificationType.MESSAGE, NotificationChannel.EMAIL))
                .isFalse();
    }
}
