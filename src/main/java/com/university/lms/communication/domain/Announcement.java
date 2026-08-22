package com.university.lms.communication.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A broadcast from staff to a group of people.
 *
 * <p>The audience is expressed as a type plus the id of the thing it scopes to, rather than as a
 * materialised recipient list. A section announcement addressed to "everyone enrolled" must follow
 * the enrolment list as it changes; freezing recipients at publication time would silently exclude
 * every student who joined afterwards.
 */
@Entity
@Table(
        name = "announcements",
        indexes = {
            @Index(name = "idx_announcements_audience", columnList = "audience,audience_ref_id"),
            @Index(name = "idx_announcements_published", columnList = "published_at")
        })
@Getter
public class Announcement extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 30)
    private AnnouncementAudience audience;

    /** Id of the faculty/department/programme/section scoped to; null for university-wide. */
    @Column(name = "audience_ref_id")
    private UUID audienceRefId;

    /** Cross-module reference into identity. */
    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected Announcement() {
        // for JPA
    }

    public Announcement(
            String title, String body, AnnouncementAudience audience, UUID audienceRefId, UUID authorUserId) {
        this.title = title;
        this.body = body;
        this.audience = audience;
        this.audienceRefId = audienceRefId;
        this.authorUserId = authorUserId;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void publish(Instant at) {
        this.publishedAt = at;
    }
}
