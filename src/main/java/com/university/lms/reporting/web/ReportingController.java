package com.university.lms.reporting.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.reporting.dto.TermCensusResponse;
import com.university.lms.reporting.service.ReportingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Institutional aggregates — see {@code ReportingService} for what each report actually reads. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/terms/{id}/census")
    public TermCensusResponse termCensus(@PathVariable UUID id) {
        return reportingService.termCensus(id);
    }
}
