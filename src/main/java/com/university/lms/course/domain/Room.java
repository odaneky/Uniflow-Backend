package com.university.lms.course.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.Getter;

/**
 * A schedulable room and its seating capacity. Matched against a meeting's free-text {@code room}
 * field by {@link #normalizedCode}, computed the same way {@code TeachingConflictChecker} already
 * normalizes room strings for its own clash check — "Lab 3" and "Lab-3" resolve to the same room
 * here too.
 *
 * <p>{@code section_meetings.room} stays free text rather than a foreign key to this table: this
 * registry is additive, not a prerequisite for scheduling, since not every meeting's room has
 * necessarily been registered here yet.
 */
@Entity
@Table(
        name = "rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_rooms_normalized_code", columnNames = "normalized_code"))
@Getter
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rooms_building"))
    private Building building;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "normalized_code", nullable = false, length = 50)
    private String normalizedCode;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    protected Room() {}

    public Room(Building building, String code, int capacity) {
        this.building = building;
        this.code = code;
        this.normalizedCode = normalize(code);
        this.capacity = capacity;
    }

    /** Mirrors {@code TeachingConflictChecker.normaliseRoom} exactly — both sides of a match must agree. */
    public static String normalize(String room) {
        if (room == null || room.isBlank()) {
            return null;
        }
        return room.trim().toLowerCase(Locale.ROOT).replaceAll("[ \\-_.]", "");
    }
}
