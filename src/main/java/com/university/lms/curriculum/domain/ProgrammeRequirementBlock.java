package com.university.lms.curriculum.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * A named slice of a programme's curriculum, e.g. "Core Computer Science".
 *
 * <p>{@code kind} says whether listed courses are core or elective <em>for this programme</em>.
 * Course ids are opaque references into the catalog — this module never reads the courses table.
 */
@Entity
@Table(
        name = "programme_requirement_blocks",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_requirement_blocks_programme_name", columnNames = {"programme_id", "name"}),
        indexes = @Index(name = "idx_requirement_blocks_programme", columnList = "programme_id"))
@Getter
public class ProgrammeRequirementBlock extends BaseEntity {

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private RequirementKind kind;

    @Column(name = "required_credits", nullable = false)
    private int requiredCredits;

    @Column(name = "position", nullable = false)
    private int position;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "programme_requirement_courses",
            joinColumns =
                    @JoinColumn(
                            name = "block_id",
                            nullable = false,
                            foreignKey = @ForeignKey(name = "fk_requirement_courses_block")))
    @Column(name = "course_id", nullable = false)
    private Set<UUID> courseIds = new LinkedHashSet<>();

    protected ProgrammeRequirementBlock() {
        // for JPA
    }

    public ProgrammeRequirementBlock(
            UUID programmeId, String name, RequirementKind kind, int requiredCredits, int position) {
        this.programmeId = programmeId;
        this.name = name;
        this.kind = kind;
        this.requiredCredits = requiredCredits;
        this.position = position;
    }

    public void addCourse(UUID courseId) {
        this.courseIds.add(courseId);
    }
}
