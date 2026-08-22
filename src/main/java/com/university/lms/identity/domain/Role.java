package com.university.lms.identity.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

/**
 * A named bundle of {@link Permission}s, e.g. {@code REGISTRAR}.
 *
 * <p>The association to permissions is unidirectional and lazy: a permission has no reason to know
 * which roles reference it, and eager-loading the set would drag it into every query that merely
 * needs a role's name.
 */
@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uk_roles_name", columnNames = "name"))
@Getter
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_role_permissions_role")),
            inverseJoinColumns = @JoinColumn(name = "permission_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_role_permissions_permission")))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // for JPA
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions);
    }

    public void grant(Permission permission) {
        permissions.add(permission);
    }

    public void revoke(Permission permission) {
        permissions.remove(permission);
    }
}
