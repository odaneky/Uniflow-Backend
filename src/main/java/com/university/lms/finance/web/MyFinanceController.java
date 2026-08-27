package com.university.lms.finance.web;

import com.university.lms.finance.dto.AccountResponse;
import com.university.lms.finance.dto.CreatePaymentRequest;
import com.university.lms.finance.dto.InvoiceResponse;
import com.university.lms.finance.dto.OnlinePaymentResponse;
import com.university.lms.finance.dto.PaymentResponse;
import com.university.lms.finance.service.FinanceService;
import com.university.lms.finance.service.InvoiceService;
import com.university.lms.finance.service.OnlinePaymentService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MyFinanceController {

    private final FinanceService financeService;
    private final InvoiceService invoiceService;
    private final OnlinePaymentService onlinePaymentService;

    public MyFinanceController(
            FinanceService financeService, InvoiceService invoiceService, OnlinePaymentService onlinePaymentService) {
        this.financeService = financeService;
        this.invoiceService = invoiceService;
        this.onlinePaymentService = onlinePaymentService;
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

    /** E7: starts a hosted checkout; the response is a redirect URL, never a card field. */
    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping("/account/payments/online")
    public OnlinePaymentResponse payOnline(@Valid @RequestBody CreatePaymentRequest request) {
        return onlinePaymentService.initiate(request);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices() {
        return invoiceService.own();
    }
}
