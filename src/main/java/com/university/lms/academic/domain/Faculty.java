package com.university.lms.academic.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

/**
 * Top level of the academic hierarchy: Faculty → Department → Programme.
 *
 * <p>No collection of departments is mapped here. The relationship is navigated from the child
 * side via {@code DepartmentRepository.findByFacultyId}, which keeps loading a faculty cheap and
 * removes any temptation to walk an unbounded collection in application code.
 */
@Entity
@Table(name = "faculties", uniqueConstraints = @UniqueConstraint(name = "uk_faculties_code", columnNames = "code"))
@Getter
public class Faculty extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Cross-module reference into identity, held as an id rather than an association. */
    @Column(name = "dean_user_id")
    private UUID deanUserId;

    protected Faculty() {
        // for JPA
    }

    public Faculty(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void assignDean(UUID deanUserId) {
        this.deanUserId = deanUserId;
    }
}
