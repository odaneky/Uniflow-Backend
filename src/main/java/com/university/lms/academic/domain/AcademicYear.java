package com.university.lms.academic.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;

/** A session such as {@code 2026/2027}, containing one or more {@link AcademicTerm}s. */
@Entity
@Table(
        name = "academic_years",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_years_code", columnNames = "code"))
@Getter
public class AcademicYear extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected AcademicYear() {
        // for JPA
    }

    public AcademicYear(String code, LocalDate startDate, LocalDate endDate) {
        this.code = code;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
