package com.university.lms.grading.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;

/** One band of a {@link GradeScale}, e.g. {@code A} for 80.00–100.00 worth 4.00 points. */
@Entity
@Table(name = "grade_scale_bands", indexes = @Index(name = "idx_grade_scale_bands_scale", columnList = "grade_scale_id"))
@Getter
public class GradeScaleBand extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "grade_scale_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_grade_scale_bands_scale"))
    private GradeScale gradeScale;

    @Column(name = "letter", nullable = false, length = 5)
    private String letter;

    @Column(name = "min_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercent;

    @Column(name = "max_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercent;

    @Column(name = "grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoint;

    protected GradeScaleBand() {
        // for JPA
    }

    public GradeScaleBand(
            GradeScale gradeScale,
            String letter,
            BigDecimal minPercent,
            BigDecimal maxPercent,
            BigDecimal gradePoint) {
        this.gradeScale = gradeScale;
        this.letter = letter;
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
        this.gradePoint = gradePoint;
    }

    public boolean contains(BigDecimal percentage) {
        return percentage.compareTo(minPercent) >= 0 && percentage.compareTo(maxPercent) <= 0;
    }
}
