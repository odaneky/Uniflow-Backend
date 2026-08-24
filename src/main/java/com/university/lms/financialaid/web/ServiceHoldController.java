package com.university.lms.financialaid.web;

import com.university.lms.financialaid.dto.ServiceHoldMutationRequest;
import com.university.lms.financialaid.dto.ServiceHoldResponse;
import com.university.lms.financialaid.service.ServiceHoldService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-holds/students/{studentId}")
public class ServiceHoldController {

    private final ServiceHoldService serviceHoldService;

    public ServiceHoldController(ServiceHoldService serviceHoldService) {
        this.serviceHoldService = serviceHoldService;
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping
    public List<ServiceHoldResponse> list(@PathVariable UUID studentId) {
        return serviceHoldService.allHoldsFor(studentId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping
    public ServiceHoldResponse mutate(
            @PathVariable UUID studentId, @Valid @RequestBody ServiceHoldMutationRequest request) {
        if (request.action() == ServiceHoldMutationRequest.Action.CLEAR) {
            if (request.holdId() == null) {
                throw new ValidationException(FinancialAidErrorCode.HOLD_NOT_FOUND, "holdId is required to clear a hold");
            }
            return serviceHoldService.clearHold(request.holdId());
        }
        if (request.holdType() == null || request.reason() == null || request.reason().isBlank()) {
            throw new ValidationException(
                    FinancialAidErrorCode.HOLD_NOT_FOUND, "holdType and reason are required to place a hold");
        }
        return serviceHoldService.placeHold(studentId, request.holdType(), request.reason());
    }
}
