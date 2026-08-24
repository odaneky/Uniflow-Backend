package com.university.lms.staffing.dto;

import com.university.lms.staffing.domain.ContractType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterEmployeeRequest(
        @NotNull(message = "is required") UUID userId,
        @Size(max = 30, message = "must be at most 30 characters") String employeeNumber,
        @NotNull(message = "is required") ContractType contractType,
        @DecimalMin(value = "0.01", message = "must be greater than 0")
                @DecimalMax(value = "1.00", message = "must be at most 1.00")
                BigDecimal fte,
        @PastOrPresent(message = "must not be in the future") LocalDate hiredOn) {}
