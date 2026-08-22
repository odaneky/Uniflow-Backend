package com.university.lms.notification.web;

import com.university.lms.notification.dto.CreateNotificationRequest;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request) {
        NotificationResponse created = notificationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/notifications/" + created.id())).body(created);
    }
}
