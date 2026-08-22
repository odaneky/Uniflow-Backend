package com.university.lms.finance.web;

import com.university.lms.finance.dto.ReplaceProgrammeTuitionRateRequest;
import com.university.lms.finance.dto.ReplaceTuitionScheduleRequest;
import com.university.lms.finance.dto.TuitionScheduleResponse;
import com.university.lms.finance.service.TuitionScheduleService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Institution tuition and campus fee, with optional per-programme per-credit overrides. */
@RestController
@RequestMapping("/api/v1/tuition-schedule")
public class TuitionScheduleController {

    private final TuitionScheduleService tuitionScheduleService;

    public TuitionScheduleController(TuitionScheduleService tuitionScheduleService) {
        this.tuitionScheduleService = tuitionScheduleService;
    }

    @GetMapping
    public TuitionScheduleResponse find() {
        return tuitionScheduleService.find();
    }

    @PutMapping
    public TuitionScheduleResponse replace(@Valid @RequestBody ReplaceTuitionScheduleRequest request) {
        return tuitionScheduleService.replace(request);
    }

    @PutMapping("/programmes/{programmeId}")
    public TuitionScheduleResponse replaceProgrammeRate(
            @PathVariable UUID programmeId, @Valid @RequestBody ReplaceProgrammeTuitionRateRequest request) {
        return tuitionScheduleService.replaceProgrammeRate(programmeId, request);
    }

    @DeleteMapping("/programmes/{programmeId}")
    public TuitionScheduleResponse clearProgrammeRate(@PathVariable UUID programmeId) {
        return tuitionScheduleService.clearProgrammeRate(programmeId);
    }
}
