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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;

/**
 * A named slice of one {@link CurriculumVersion}'s curriculum, e.g. "Core Computer Science".
 *
 * <p>{@code kind} says whether listed courses are core or elective <em>for this version</em>.
 * Course ids are opaque references into the catalog — this module never reads the courses table.
 *
 * <p>Belongs to a version, not directly to a programme: {@code CurriculumService} refuses to write
 * to a block whose version {@link CurriculumVersion#isEditable()} returns false, which is what
 * makes a published curriculum year immutable.
 */
@Entity
@Table(
        name = "programme_requirement_blocks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_requirement_blocks_version_name",
                        columnNames = {"curriculum_version_id", "name"}),
        indexes = @Index(name = "idx_requirement_blocks_version", columnList = "curriculum_version_id"))
@Getter
public class ProgrammeRequirementBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "curriculum_version_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_requirement_blocks_version"))
    private CurriculumVersion curriculumVersion;

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
            CurriculumVersion curriculumVersion,
            String name,
            RequirementKind kind,
            int requiredCredits,
            int position) {
        this.curriculumVersion = curriculumVersion;
        this.name = name;
        this.kind = kind;
        this.requiredCredits = requiredCredits;
        this.position = position;
    }

    public void addCourse(UUID courseId) {
        this.courseIds.add(courseId);
    }

    /** Convenience: the programme this block ultimately belongs to, through its version. */
    public UUID getProgrammeId() {
        return curriculumVersion.getProgrammeId();
    }
}
