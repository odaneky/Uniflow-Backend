package com.university.lms.communication.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** A message thread between two or more participants. */
@Entity
@Table(name = "conversations")
@Getter
public class Conversation extends BaseEntity {

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** Cross-module reference into identity. */
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    /** Cross-module reference into course; set when the thread is about a specific section. */
    @Column(name = "course_section_id")
    private UUID courseSectionId;

    protected Conversation() {
        // for JPA
    }

    public Conversation(String subject, UUID createdByUserId) {
        this.subject = subject;
        this.createdByUserId = createdByUserId;
    }

    public void attachToSection(UUID courseSectionId) {
        this.courseSectionId = courseSectionId;
    }
}
