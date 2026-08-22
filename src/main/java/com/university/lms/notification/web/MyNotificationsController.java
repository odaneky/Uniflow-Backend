package com.university.lms.notification.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.notification.dto.NotificationResponse;
import com.university.lms.notification.service.MyNotificationsService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> notifications(@PageableDefault(size = 20) Pageable pageable) {
        return myNotificationsService.own(pageable);
    }
}
