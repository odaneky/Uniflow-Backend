package com.university.lms.finance.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "student_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_student_accounts_student", columnNames = "student_id"))
@Getter
public class StudentAccount extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "due_on")
    private LocalDate dueOn;

    protected StudentAccount() {}

    public StudentAccount(UUID studentId, String currency) {
        this.studentId = studentId;
        this.currency = currency;
    }

    public void dueOn(LocalDate dueOn) {
        this.dueOn = dueOn;
    }
}
