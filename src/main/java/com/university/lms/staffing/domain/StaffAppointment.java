package com.university.lms.staffing.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * The scope source A5 needs and nothing currently provides: this user, in this role, appointed to
 * this org unit, for this period. {@code CurrentUser.isStaff()} today answers "is this any
 * non-student role" with no notion of which unit — a FACULTY_ADMIN token can act on every faculty's
 * records, not just the one they administer. Org-scoped authorization that actually restricts by
 * appointment is separate work; this is only the record of who is appointed where.
 *
 * <p>{@code role} matches {@code common.security.SecurityRoles} for now — appointment-specific
 * titles distinct from the coarse realm role are a later refinement, not needed for a unit to
 * exist to scope against.
 *
 * <p>No uniqueness narrower than the row: a person may hold more than one open appointment
 * concurrently (a lecturer who also advises), so two appointments for the same user in the same
 * unit are not a conflict to prevent.
 */
@Entity
@Table(
        name = "staff_appointments",
        indexes = {
            @Index(name = "idx_staff_appointments_user", columnList = "user_id"),
            @Index(name = "idx_staff_appointments_org_unit", columnList = "org_unit_id")
        })
@Getter
public class StaffAppointment extends BaseEntity {

    /** Cross-module reference into identity. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_unit_id", nullable = false, foreignKey = @ForeignKey(name = "fk_staff_appointments_org_unit"))
    private OrgUnit orgUnit;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    protected StaffAppointment() {
        // for JPA
    }

    public StaffAppointment(UUID userId, OrgUnit orgUnit, String role, LocalDate validFrom) {
        this.userId = userId;
        this.orgUnit = orgUnit;
        this.role = role;
        this.validFrom = validFrom;
    }

    public boolean isActiveOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    public void end(LocalDate validTo) {
        if (validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("An appointment cannot end before it started");
        }
        this.validTo = validTo;
    }
}
