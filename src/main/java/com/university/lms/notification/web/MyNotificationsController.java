package com.university.lms.notification.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.service.MyNotificationsService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own notifications. */
@RestController
@RequestMapping("/api/v1/me")
public class MyNotificationsController {

    private final MyNotificationsService myNotificationsService;

    public MyNotificationsController(MyNotificationsService myNotificationsService) {
        this.myNotificationsService = myNotificationsService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> notifications(@PageableDefault(size = 20) Pageable pageable) {
        return myNotificationsService.own(pageable);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(myNotificationsService.unreadCount());
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return myNotificationsService.markRead(id);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping("/notifications/read-all")
    public void markAllRead() {
        myNotificationsService.markAllRead();
    }

    public record UnreadCountResponse(long count) {}
}
