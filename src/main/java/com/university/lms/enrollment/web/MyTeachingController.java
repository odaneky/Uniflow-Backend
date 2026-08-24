package com.university.lms.enrollment.web;

import com.university.lms.course.dto.TeachingSectionResponse;
import com.university.lms.enrollment.service.TeachingLoadService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's teaching assignments. Lives in enrolment so live seat counts stay with the roster. */
@RestController
@RequestMapping("/api/v1/me")
public class MyTeachingController {

    private final TeachingLoadService teachingLoadService;

    public MyTeachingController(TeachingLoadService teachingLoadService) {
        this.teachingLoadService = teachingLoadService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/teaching")
    public List<TeachingSectionResponse> teaching() {
        return teachingLoadService.ownSections();
    }
}
