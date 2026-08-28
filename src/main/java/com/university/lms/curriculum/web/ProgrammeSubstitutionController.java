package com.university.lms.curriculum.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.curriculum.dto.CourseSubstitutionResponse;
import com.university.lms.curriculum.service.CurriculumService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registrar-approved course substitutions granted against a programme's required courses — the
 * read side of the exceptions a degree audit is resolved against. The write side lives in the
 * request-fulfilment workflow ({@code curriculum.api.CourseSubstitutions}), not here.
 */
@RestController
@RequestMapping("/api/v1/programmes/{programmeId}/course-substitutions")
public class ProgrammeSubstitutionController {

    private final CurriculumService curriculumService;

    public ProgrammeSubstitutionController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping
    public List<CourseSubstitutionResponse> list(@PathVariable UUID programmeId) {
        return curriculumService.substitutionsOfProgramme(programmeId);
    }
}
