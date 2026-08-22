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
        name = "forum_posts",
        indexes = @Index(name = "idx_forum_posts_topic_sent", columnList = "topic_id,sent_at,id"))
@Getter
public class ForumPost extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, foreignKey = @ForeignKey(name = "fk_forum_posts_topic"))
    private ForumTopic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_post_id", foreignKey = @ForeignKey(name = "fk_forum_posts_parent"))
    private ForumPost parentPost;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    protected ForumPost() {}

    public ForumPost(ForumTopic topic, UUID authorUserId, String body, ForumPost parentPost) {
        this.topic = topic;
        this.authorUserId = authorUserId;
        this.body = body;
        this.parentPost = parentPost;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String visibleBody() {
        return isDeleted() ? "[Post removed]" : body;
    }

    public void softDelete(UUID moderatorUserId, Instant at) {
        this.deletedAt = at;
        this.deletedByUserId = moderatorUserId;
    }
}
