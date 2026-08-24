package com.university.lms.grading.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.grading.dto.AcademicSummaryResponse;
import com.university.lms.grading.dto.GradeResponse;
import com.university.lms.grading.service.GradeService;
import com.university.lms.grading.service.MyGradesService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own published grades. No identifier is accepted, by design. */
@RestController
@RequestMapping("/api/v1/me")
public class MyGradesController {

    private final MyGradesService myGradesService;
    private final GradeService gradeService;

    public MyGradesController(MyGradesService myGradesService, GradeService gradeService) {
        this.myGradesService = myGradesService;
        this.gradeService = gradeService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/grades")
    public PageResponse<GradeResponse> grades(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return myGradesService.ownGrades(pageable);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/academic-summary")
    public AcademicSummaryResponse academicSummary() {
        return gradeService.ownSummary();
    }
}
