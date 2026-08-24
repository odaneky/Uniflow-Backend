package com.university.lms.admissions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Stored field list for a programme's admissions application. */
@Entity
@Table(name = "programme_application_forms")
@Getter
public class ProgrammeApplicationForm {

    @Id
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", nullable = false, columnDefinition = "jsonb")
    private String fieldsJson;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProgrammeApplicationForm() {
        // for JPA
    }

    public ProgrammeApplicationForm(UUID programmeId, String fieldsJson) {
        this.programmeId = programmeId;
        this.fieldsJson = fieldsJson;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replaceFields(String fieldsJson) {
        this.fieldsJson = fieldsJson;
        this.updatedAt = Instant.now();
    }
}
