package com.university.lms.finance.web;

import com.university.lms.finance.dto.AccountResponse;
import com.university.lms.finance.dto.CreatePaymentRequest;
import com.university.lms.finance.dto.PaymentResponse;
import com.university.lms.finance.service.FinanceService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MyFinanceController {

    private final FinanceService financeService;

    public MyFinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/account")
    public AccountResponse account() {
        return financeService.own();
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping("/account/payments")
    public PaymentResponse pay(@Valid @RequestBody CreatePaymentRequest request) {
        return financeService.payOwn(request);
    }
}
