package com.university.lms.financialaid.web;

import com.university.lms.common.exception.ValidationException;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.dto.FinancialAidAwardResponse;
import com.university.lms.financialaid.dto.PackageAwardsRequest;
import com.university.lms.financialaid.dto.StaffAwardActionRequest;
import com.university.lms.financialaid.service.FinancialAidService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
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
@RequestMapping("/api/v1/financial-aid/students/{studentId}/awards")
public class FinancialAidController {

    private final FinancialAidService financialAidService;

    public FinancialAidController(FinancialAidService financialAidService) {
        this.financialAidService = financialAidService;
    }

    @AccessClass(SELF_OR_STAFF)
    @GetMapping
    public List<FinancialAidAwardResponse> list(@PathVariable UUID studentId) {
        return financialAidService.awardsForStudent(studentId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping
    public List<FinancialAidAwardResponse> mutate(
            @PathVariable UUID studentId, @Valid @RequestBody StaffAwardActionRequest request) {
        if (request.action() == StaffAwardActionRequest.Action.DISBURSE) {
            if (request.awardId() == null) {
                throw new ValidationException(FinancialAidErrorCode.AWARD_NOT_FOUND, "awardId is required to disburse");
            }
            return List.of(financialAidService.disburse(request.awardId()));
        }
        if (request.academicTermId() == null) {
            throw new ValidationException(
                    FinancialAidErrorCode.AWARD_INVALID_STATE, "academicTermId is required to package awards");
        }
        PackageAwardsRequest packageRequest = new PackageAwardsRequest(
                request.academicTermId(),
                request.aidYear(),
                request.pellAmount(),
                request.institutionalAmount());
        return financialAidService.packageAwards(studentId, packageRequest);
    }
}
