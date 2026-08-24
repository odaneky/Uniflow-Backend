package com.university.lms.curriculum.web;

import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.service.CurriculumService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's progress against their programme's requirement blocks. */
@RestController
@RequestMapping("/api/v1/me")
public class MyDegreeProgressController {

    private final CurriculumService curriculumService;

    public MyDegreeProgressController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/degree-progress")
    public DegreeProgressResponse own() {
        return curriculumService.ownProgress();
    }
}
