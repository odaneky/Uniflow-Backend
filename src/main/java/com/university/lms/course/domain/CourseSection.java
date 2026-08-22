package com.university.lms.course.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
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
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;

/**
 * A specific offering of a {@link Course} in a specific term — what students actually enrol in.
 *
 * <p>{@code enrolledCount} is a counter maintained on this row rather than a {@code COUNT(*)} over
 * enrolments. That is a deliberate trade: it denormalises, but it makes "is there a seat?" and
 * "take the seat" a single atomic statement instead of a read followed by a write that another
 * transaction can interleave with. The counter is only ever moved by the guarded UPDATE in
 * {@code CourseSectionRepository}, never by assignment from application code, and the database
 * carries a CHECK constraint so it cannot drift outside {@code 0..capacity} even if it were.
 */
@Entity
@Table(
        name = "course_sections",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_course_sections_course_code",
                        columnNames = {"course_id", "section_code"}),
        indexes = {
            @Index(name = "idx_course_sections_course", columnList = "course_id"),
            @Index(name = "idx_course_sections_term", columnList = "academic_term_id"),
            @Index(name = "idx_course_sections_lecturer", columnList = "lecturer_user_id")
        })
@Getter
public class CourseSection extends BaseEntity {

    /** Same module, so a real association is appropriate. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_course_sections_course"))
    private Course course;

    /** Cross-module reference into academic. */
    @Column(name = "academic_term_id", nullable = false)
    private UUID academicTermId;

    /** Distinguishes parallel offerings of this course, e.g. {@code UN1}. Unique per course, not globally. */
    @Column(name = "section_code", nullable = false, length = 20)
    private String sectionCode;

    /**
     * Primary activity when this occurrence has several components. The set itself is
     * {@code section_components}; this column is the lecture when the set includes one.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, length = 20)
    private CourseComponent component = CourseComponent.LECTURE;

    /** Cross-module reference into identity. */
    @Column(name = "lecturer_user_id")
    private UUID lecturerUserId;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CourseSectionStatus status = CourseSectionStatus.PLANNED;

    /**
     * The most contended row in the system during a registration window: every concurrent
     * enrolment attempt for this section touches it.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CourseSection() {
        // for JPA
    }

    public CourseSection(Course course, UUID academicTermId, String sectionCode, int capacity) {
        this(course, academicTermId, sectionCode, capacity, CourseComponent.LECTURE);
    }

    public CourseSection(
            Course course, UUID academicTermId, String sectionCode, int capacity, CourseComponent component) {
        this.course = course;
        this.academicTermId = academicTermId;
        this.sectionCode = sectionCode;
        this.capacity = capacity;
        this.component = component == null ? CourseComponent.LECTURE : component;
    }

    public boolean isOpenForEnrolment() {
        return status == CourseSectionStatus.OPEN;
    }

    public boolean hasAvailableSeats() {
        return enrolledCount < capacity;
    }

    public int availableSeats() {
        return Math.max(0, capacity - enrolledCount);
    }

    public void assignLecturer(UUID lecturerUserId) {
        this.lecturerUserId = lecturerUserId;
    }

    public void open() {
        this.status = CourseSectionStatus.OPEN;
    }

    public void close() {
        this.status = CourseSectionStatus.CLOSED;
    }

    public void cancel() {
        if (status == CourseSectionStatus.COMPLETED) {
            throw new IllegalArgumentException("A completed occurrence cannot be cancelled");
        }
        this.status = CourseSectionStatus.CANCELLED;
    }

    public void complete() {
        this.status = CourseSectionStatus.COMPLETED;
    }

    /**
     * Capacity may not be cut below the number of students already holding a seat; doing so would
     * leave the section over-subscribed with no way to reconcile it.
     */
    public void changeCapacity(int capacity) {
        if (capacity < enrolledCount) {
            throw new IllegalArgumentException("Capacity cannot be lower than the current enrolled count");
        }
        this.capacity = capacity;
    }
}
