package com.university.lms.communication.dto;

import com.university.lms.communication.domain.Announcement;
import com.university.lms.communication.domain.AnnouncementAudience;
import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String body,
        AnnouncementAudience audience,
        UUID audienceRefId,
        Instant publishedAt) {

    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getAudience(),
                announcement.getAudienceRefId(),
                announcement.getPublishedAt());
    }
}
