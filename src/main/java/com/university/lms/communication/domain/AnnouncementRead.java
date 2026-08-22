package com.university.lms.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "announcement_reads")
@IdClass(AnnouncementRead.AnnouncementReadId.class)
@Getter
@NoArgsConstructor
public class AnnouncementRead {

    @Id
    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    public AnnouncementRead(UUID announcementId, UUID userId, Instant readAt) {
        this.announcementId = announcementId;
        this.userId = userId;
        this.readAt = readAt;
    }

    public record AnnouncementReadId(UUID announcementId, UUID userId) implements Serializable {}
}
