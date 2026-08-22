package com.university.lms.finance.web;

import com.university.lms.finance.dto.AccountResponse;
import com.university.lms.finance.dto.CreateAccountEntryRequest;
import com.university.lms.finance.service.FinanceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final FinanceService financeService;

    public AccountController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/{studentId}")
    public AccountResponse forStudent(@PathVariable UUID studentId) {
        return financeService.forStudent(studentId);
    }

    @PostMapping("/{studentId}/entries")
    public AccountResponse addEntry(
            @PathVariable UUID studentId, @Valid @RequestBody CreateAccountEntryRequest request) {
        return financeService.addEntry(studentId, request);
    }
}
