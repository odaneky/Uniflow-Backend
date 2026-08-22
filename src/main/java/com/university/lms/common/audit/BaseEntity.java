package com.university.lms.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identity and audit columns shared by every persistent entity.
 *
 * <p>Two decisions here are deliberate and worth understanding before changing them:
 *
 * <p><b>The id is assigned in Java, not by the database.</b> That makes {@code id} non-null from
 * construction, which in turn makes {@link #equals(Object)} and {@link #hashCode()} correct and
 * stable even before the entity is persisted — the usual JPA equality trap. The cost is that
 * Spring Data can no longer infer newness from a null id, which is why this class implements
 * {@link Persistable}: without it every {@code save()} would issue a redundant SELECT before its
 * INSERT.
 *
 * <p><b>{@code @Version} is not here.</b> Optimistic locking belongs on the entities that are
 * actually contended (see {@code docs/concurrency.md}); putting it on every row would add a write
 * conflict surface to reference data that is never concurrently mutated.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    /** Visible for repository tests that need to pin a known id; not part of normal usage. */
    protected void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * Equality is by identifier and effective type. {@link Hibernate#getClass} unwraps lazy
     * proxies, so a proxy and its target compare equal — comparing {@code getClass()} directly
     * would not.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof BaseEntity that)) {
            return false;
        }
        if (!Hibernate.getClass(this).equals(Hibernate.getClass(other))) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return Hibernate.getClass(this).getSimpleName() + "{id=" + id + "}";
    }
}
