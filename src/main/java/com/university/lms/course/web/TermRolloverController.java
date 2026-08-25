package com.university.lms.course.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.course.dto.RolloverTermRequest;
import com.university.lms.course.dto.TermRolloverResponse;
import com.university.lms.course.service.TermRolloverService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** D7: carries a term's sections forward into a new term. */
@RestController
@RequestMapping("/api/v1/academic-terms")
public class TermRolloverController {

    private final TermRolloverService termRolloverService;

    public TermRolloverController(TermRolloverService termRolloverService) {
        this.termRolloverService = termRolloverService;
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/{sourceTermId}/rollover")
    public TermRolloverResponse rollover(
            @PathVariable UUID sourceTermId, @Valid @RequestBody RolloverTermRequest request) {
        return termRolloverService.rollover(sourceTermId, request.targetTermId(), request.dryRun());
    }
}
