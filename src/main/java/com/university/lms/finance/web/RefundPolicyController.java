package com.university.lms.finance.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.finance.dto.ReplaceRefundPolicyRequest;
import com.university.lms.finance.dto.RefundPolicyResponse;
import com.university.lms.finance.service.RefundPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The institution's withdrawal refund taper. */
@RestController
@RequestMapping("/api/v1/refund-policy")
public class RefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    public RefundPolicyController(RefundPolicyService refundPolicyService) {
        this.refundPolicyService = refundPolicyService;
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping
    public RefundPolicyResponse find() {
        return refundPolicyService.find();
    }

    @AccessClass(REGISTRY_ONLY)
    @PutMapping
    public RefundPolicyResponse replace(@Valid @RequestBody ReplaceRefundPolicyRequest request) {
        return refundPolicyService.replace(request);
    }
}
