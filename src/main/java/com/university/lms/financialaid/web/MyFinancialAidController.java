package com.university.lms.financialaid.web;

import com.university.lms.financialaid.dto.FinancialAidAwardResponse;
import com.university.lms.financialaid.service.FinancialAidService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/financial-aid/awards")
public class MyFinancialAidController {

    private final FinancialAidService financialAidService;

    public MyFinancialAidController(FinancialAidService financialAidService) {
        this.financialAidService = financialAidService;
    }

    @GetMapping
    public List<FinancialAidAwardResponse> ownAwards() {
        return financialAidService.ownAwards();
    }

    @PostMapping("/{awardId}/accept")
    public FinancialAidAwardResponse accept(@PathVariable UUID awardId) {
        return financialAidService.acceptOwnAward(awardId);
    }
}
