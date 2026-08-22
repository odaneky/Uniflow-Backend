package com.university.lms.communication.api;

import com.university.lms.communication.domain.AnnouncementAudience;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only announcement lookups for notification dispatch and audience resolution. */
public interface AnnouncementDirectory {

    record AnnouncementSummary(
            UUID id,
            String title,
            String bodyPreview,
            AnnouncementAudience audience,
            UUID audienceRefId,
            UUID authorUserId) {}

    Optional<AnnouncementSummary> findPublishedById(UUID announcementId);

    List<UUID> recipientUserIds(AnnouncementSummary announcement);
}
