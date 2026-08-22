package com.university.lms.communication.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "forum_topics",
        indexes = @Index(name = "idx_forum_topics_section", columnList = "course_section_id,pinned,updated_at"))
@Getter
public class ForumTopic extends BaseEntity {

    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    protected ForumTopic() {}

    public ForumTopic(UUID courseSectionId, String title, UUID authorUserId) {
        this.courseSectionId = courseSectionId;
        this.title = title;
        this.authorUserId = authorUserId;
    }

    public void pin(boolean value) {
        this.pinned = value;
    }

    public void lock(boolean value) {
        this.locked = value;
    }
}
