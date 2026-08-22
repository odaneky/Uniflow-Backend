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

/** A single message within a {@link Conversation}. */
@Entity
@Table(
        name = "messages",
        indexes = @Index(name = "idx_messages_conversation_sent", columnList = "conversation_id,sent_at"))
@Getter
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "conversation_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_messages_conversation"))
    private Conversation conversation;

    /** Cross-module reference into identity. */
    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    protected Message() {
        // for JPA
    }

    public Message(Conversation conversation, UUID senderUserId, String body) {
        this.conversation = conversation;
        this.senderUserId = senderUserId;
        this.body = body;
    }
}
