package com.university.lms.enrollment.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.enrollment.dto.SectionCancellationResponse;
import com.university.lms.enrollment.service.SectionCancellationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cancelling a section, with its full consequences for enrolled students — see {@code
 * SectionCancellationService} for why this lives here rather than in {@code course.web}.
 */
@RestController
@RequestMapping("/api/v1/courses/sections")
public class SectionCancellationController {

    private final SectionCancellationService sectionCancellationService;

    public SectionCancellationController(SectionCancellationService sectionCancellationService) {
        this.sectionCancellationService = sectionCancellationService;
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/{sectionId}/cancel")
    public SectionCancellationResponse cancel(@PathVariable UUID sectionId) {
        return sectionCancellationService.cancel(sectionId);
    }
}
