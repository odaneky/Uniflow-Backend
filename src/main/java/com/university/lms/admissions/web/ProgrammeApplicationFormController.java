package com.university.lms.admissions.web;

import com.university.lms.admissions.dto.ProgrammeApplicationFormResponse;
import com.university.lms.admissions.dto.ReplaceProgrammeApplicationFormRequest;
import com.university.lms.admissions.service.ProgrammeApplicationFormService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-programme admissions application form configuration. */
@RestController
@RequestMapping("/api/v1/programmes/{programmeId}/application-form")
public class ProgrammeApplicationFormController {

    private final ProgrammeApplicationFormService formService;

    public ProgrammeApplicationFormController(ProgrammeApplicationFormService formService) {
        this.formService = formService;
    }

    @AccessClass(PUBLIC)
    @GetMapping
    public ProgrammeApplicationFormResponse find(@PathVariable UUID programmeId) {
        return formService.findByProgrammeId(programmeId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PutMapping
    public ProgrammeApplicationFormResponse replace(
            @PathVariable UUID programmeId, @Valid @RequestBody ReplaceProgrammeApplicationFormRequest request) {
        return formService.replace(programmeId, request);
    }
}
