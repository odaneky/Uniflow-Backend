package com.university.lms.identity.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * A single named capability, e.g. {@code COURSE_CREATE}.
 *
 * <p>Permissions exist as rows rather than as a Java enum so that the authorization model can be
 * changed by an administrator without a redeploy — which is the whole point of not hard-coding
 * authorization logic through the application.
 */
@Entity
@Table(
        name = "permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_permissions_name", columnNames = "name"))
@Getter
public class Permission extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    protected Permission() {
        // for JPA
    }

    public Permission(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void describe(String description) {
        this.description = description;
    }
}
