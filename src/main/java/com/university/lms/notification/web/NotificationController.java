package com.university.lms.notification.web;

import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.dto.CreateNotificationRequest;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.service.NotificationService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request) {
        NotificationResponse created = notificationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/notifications/" + created.id())).body(created);
    }

    @AccessClass(STAFF_ONLY)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Seed/admin helper: drop all notifications of a type for one recipient. */
    @AccessClass(STAFF_ONLY)
    @DeleteMapping
    public ResponseEntity<Void> deleteForRecipient(
            @RequestParam UUID recipientUserId, @RequestParam NotificationType type) {
        notificationService.deleteByRecipientAndType(recipientUserId, type);
        return ResponseEntity.noContent().build();
    }
}
