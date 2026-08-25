package com.university.lms.course.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/** A physical building on campus — the container {@link Room} capacities are grouped under. */
@Entity
@Table(name = "buildings", uniqueConstraints = @UniqueConstraint(name = "uk_buildings_code", columnNames = "code"))
@Getter
public class Building extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    protected Building() {}

    public Building(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
