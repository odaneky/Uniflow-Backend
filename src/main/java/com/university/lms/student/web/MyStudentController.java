package com.university.lms.student.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.student.dto.AdviseeSummaryResponse;
import com.university.lms.student.dto.AdvisorOfficeHoursResponse;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.dto.UpdateAdvisorOfficeHoursRequest;
import com.university.lms.student.dto.UpdateOwnProfileRequest;
import com.university.lms.student.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own academic record.
 *
 * <p>Takes no identifier: the record is determined by the authenticated principal. That is what
 * makes this path structurally safe — there is no parameter for a client to tamper with, so no
 * equivalent of {@code GET /students/{someone-else}} exists here to be got wrong.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MyStudentController {

    private final StudentService studentService;

    public MyStudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /** {@code 404} when the caller has no student record — staff, or an identity not yet enrolled. */
    @GetMapping("/profile")
    public StudentResponse profile() {
        return studentService.findOwn();
    }

    @PatchMapping("/profile")
    public StudentResponse updateProfile(@Valid @RequestBody UpdateOwnProfileRequest request) {
        return studentService.updateOwnProfile(request);
    }

    /** Students assigned to the caller as academic advisees. */
    @GetMapping("/advisees")
    public PageResponse<AdviseeSummaryResponse> advisees(
            @PageableDefault(size = 50, sort = "studentNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return studentService.listOwnAdvisees(pageable);
    }

    /** Office hours the caller has posted for advisees. */
    @GetMapping("/advisor/office-hours")
    public AdvisorOfficeHoursResponse advisorOfficeHours() {
        return studentService.findOwnAdvisorOfficeHours();
    }

    @PatchMapping("/advisor/office-hours")
    public AdvisorOfficeHoursResponse updateAdvisorOfficeHours(
            @Valid @RequestBody UpdateAdvisorOfficeHoursRequest request) {
        return studentService.updateOwnAdvisorOfficeHours(request.officeHours());
    }
}
