package com.university.lms.notification.web;

import com.university.lms.common.sse.LocalSseEventPublisher;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.notification.dto.NotificationPreferenceResponse;
import com.university.lms.notification.dto.UpdateNotificationPreferencesRequest;
import com.university.lms.notification.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/me")
public class MyEventsController {

    private final CurrentUserProvider currentUserProvider;
    private final LocalSseEventPublisher sseEventPublisher;
    private final NotificationPreferenceService preferenceService;
    private final long sseTimeoutMs;

    public MyEventsController(
            CurrentUserProvider currentUserProvider,
            LocalSseEventPublisher sseEventPublisher,
            NotificationPreferenceService preferenceService,
            @Value("${lms.notifications.sse.timeout-ms:300000}") long sseTimeoutMs) {
        this.currentUserProvider = currentUserProvider;
        this.sseEventPublisher = sseEventPublisher;
        this.preferenceService = preferenceService;
        this.sseTimeoutMs = sseTimeoutMs;
    }

    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseEventPublisher.subscribe(currentUserProvider.require().userId(), sseTimeoutMs);
    }

    @GetMapping("/notification-preferences")
    public List<NotificationPreferenceResponse> preferences() {
        return preferenceService.listForUser(currentUserProvider.require().userId());
    }

    @PutMapping("/notification-preferences")
    public List<NotificationPreferenceResponse> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return preferenceService.update(currentUserProvider.require().userId(), request);
    }
}
