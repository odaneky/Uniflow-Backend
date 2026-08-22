package com.university.lms.finance.web;

import com.university.lms.finance.dto.PaymentPlanResponse;
import com.university.lms.finance.dto.ReplacePaymentPlanRequest;
import com.university.lms.finance.service.PaymentPlanService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Term-scoped installment schedule. Administration decides the percents, weeks, and sanctions. */
@RestController
@RequestMapping("/api/v1/payment-plans")
public class PaymentPlanController {

    private final PaymentPlanService paymentPlanService;

    public PaymentPlanController(PaymentPlanService paymentPlanService) {
        this.paymentPlanService = paymentPlanService;
    }

    @GetMapping("/{academicTermId}")
    public PaymentPlanResponse find(@PathVariable UUID academicTermId) {
        return paymentPlanService.find(academicTermId);
    }

    @PutMapping("/{academicTermId}")
    public PaymentPlanResponse replace(
            @PathVariable UUID academicTermId, @Valid @RequestBody ReplacePaymentPlanRequest request) {
        return paymentPlanService.replace(academicTermId, request);
    }
}
