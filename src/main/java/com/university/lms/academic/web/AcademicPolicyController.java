package com.university.lms.academic.web;

import com.university.lms.academic.dto.AcademicPolicyResponse;
import com.university.lms.academic.dto.ReplaceAcademicPolicyRequest;
import com.university.lms.academic.service.AcademicPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** University default semester credit load. Programmes may override on their own resource. */
@RestController
@RequestMapping("/api/v1/academic-policy")
public class AcademicPolicyController {

    private final AcademicPolicyService academicPolicyService;

    public AcademicPolicyController(AcademicPolicyService academicPolicyService) {
        this.academicPolicyService = academicPolicyService;
    }

    @GetMapping
    public AcademicPolicyResponse find() {
        return academicPolicyService.institutionPolicy();
    }

    @PutMapping
    public AcademicPolicyResponse replace(@Valid @RequestBody ReplaceAcademicPolicyRequest request) {
        return academicPolicyService.replaceInstitution(request);
    }
}
