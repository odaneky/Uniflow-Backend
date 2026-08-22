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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Membership of a {@link Conversation}.
 *
 * <p>A first-class row rather than a plain join table because it carries per-participant state —
 * notably {@code lastReadAt}, which is what unread counts are derived from.
 */
@Entity
@Table(
        name = "conversation_participants",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_conversation_participants",
                        columnNames = {"conversation_id", "user_id"}),
        indexes = @Index(name = "idx_conversation_participants_user", columnList = "user_id"))
@Getter
public class ConversationParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "conversation_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversation_participants_conversation"))
    private Conversation conversation;

    /** Cross-module reference into identity. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    protected ConversationParticipant() {
        // for JPA
    }

    public ConversationParticipant(Conversation conversation, UUID userId) {
        this.conversation = conversation;
        this.userId = userId;
    }

    public void markReadAt(Instant at) {
        this.lastReadAt = at;
    }
}
