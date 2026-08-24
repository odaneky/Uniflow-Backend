package com.university.lms.staffing.dto;

import com.university.lms.staffing.domain.ContractType;
import com.university.lms.staffing.domain.Employee;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        UUID userId,
        String employeeNumber,
        String rank,
        ContractType contractType,
        BigDecimal fte,
        LocalDate hiredOn) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getUserId(),
                employee.getEmployeeNumber(),
                employee.getRank(),
                employee.getContractType(),
                employee.getFte(),
                employee.getHiredOn());
    }
}
