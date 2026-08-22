package com.university.lms.financialaid.web;

import com.university.lms.financialaid.dto.IsirImportResponse;
import com.university.lms.financialaid.service.FinancialAidService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/financial-aid/isir")
public class IsirImportController {

    private final FinancialAidService financialAidService;

    public IsirImportController(FinancialAidService financialAidService) {
        this.financialAidService = financialAidService;
    }

    @PostMapping("/import")
    public IsirImportResponse importCsv(@RequestBody String csv) {
        return financialAidService.importIsir(csv);
    }
}
