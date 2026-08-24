package com.university.lms.student.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;

/**
 * Office hours an advisor has posted, stored once per advisor rather than duplicated across every
 * one of their advisees' student records.
 */
@Entity
@Table(
        name = "advisor_office_hours",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_advisor_office_hours_advisor", columnNames = "advisor_user_id"))
@Getter
public class AdvisorOfficeHours extends BaseEntity {

    @Column(name = "advisor_user_id", nullable = false)
    private UUID advisorUserId;

    @Column(name = "office_hours", length = 200)
    private String officeHours;

    protected AdvisorOfficeHours() {}

    public AdvisorOfficeHours(UUID advisorUserId, String officeHours) {
        this.advisorUserId = advisorUserId;
        this.officeHours = officeHours;
    }

    public void replace(String officeHours) {
        this.officeHours = officeHours;
    }
}
