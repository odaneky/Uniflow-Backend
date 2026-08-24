package com.university.lms.staffing.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * Employment attributes for a staff member — rank, contract, FTE — kept off {@code identity.User}
 * on purpose (see that entity's own javadoc: it stays login-only, or every module coupling to it
 * grows a reason to add another nullable column).
 */
@Entity
@Table(
        name = "employees",
        uniqueConstraints = @UniqueConstraint(name = "uk_employees_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_employees_user", columnList = "user_id"))
@Getter
public class Employee extends BaseEntity {

    /** Cross-module reference into identity. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "employee_number", length = 30)
    private String employeeNumber;

    @Column(name = "rank", length = 50)
    private String rank;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 30)
    private ContractType contractType;

    @Column(name = "fte", nullable = false, precision = 3, scale = 2)
    private BigDecimal fte;

    @Column(name = "hired_on")
    private LocalDate hiredOn;

    protected Employee() {
        // for JPA
    }

    public Employee(UUID userId, String employeeNumber, ContractType contractType, BigDecimal fte, LocalDate hiredOn) {
        this.userId = userId;
        this.employeeNumber = employeeNumber;
        this.contractType = contractType;
        this.fte = fte;
        this.hiredOn = hiredOn;
    }

    public void promote(String rank) {
        this.rank = rank;
    }
}
