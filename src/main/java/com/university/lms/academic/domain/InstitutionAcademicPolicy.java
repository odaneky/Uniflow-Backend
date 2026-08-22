package com.university.lms.academic.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** One row: the university's default semester credit load and checkout correction window. */
@Entity
@Table(name = "institution_academic_policies")
@Getter
public class InstitutionAcademicPolicy extends BaseEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-000000000001");

    @Column(name = "min_semester_credits", nullable = false)
    private int minSemesterCredits;

    @Column(name = "max_semester_credits", nullable = false)
    private int maxSemesterCredits;

    /**
     * Hours after a student checkout during which they may undo that whole cart, provided
     * registration or add/drop is still open. {@code 0} disables undo.
     */
    @Column(name = "checkout_correction_hours", nullable = false)
    private int checkoutCorrectionHours = 48;

    protected InstitutionAcademicPolicy() {}

    public InstitutionAcademicPolicy(int minSemesterCredits, int maxSemesterCredits) {
        this(minSemesterCredits, maxSemesterCredits, 48);
    }

    public InstitutionAcademicPolicy(int minSemesterCredits, int maxSemesterCredits, int checkoutCorrectionHours) {
        setId(SINGLETON_ID);
        replace(minSemesterCredits, maxSemesterCredits, checkoutCorrectionHours);
    }

    public void replace(int minSemesterCredits, int maxSemesterCredits, int checkoutCorrectionHours) {
        this.minSemesterCredits = minSemesterCredits;
        this.maxSemesterCredits = maxSemesterCredits;
        this.checkoutCorrectionHours = checkoutCorrectionHours;
    }
}
