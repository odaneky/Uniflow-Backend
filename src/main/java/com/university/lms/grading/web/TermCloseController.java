package com.university.lms.grading.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.grading.dto.TermCloseResponse;
import com.university.lms.grading.service.TermCloseService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Closing a term: locking its grades and writing each student's {@code TermAcademicRecord}. */
@RestController
@RequestMapping("/api/v1/academic-terms/{id}")
public class TermCloseController {

    private final TermCloseService termCloseService;

    public TermCloseController(TermCloseService termCloseService) {
        this.termCloseService = termCloseService;
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/close")
    public TermCloseResponse close(@PathVariable UUID id) {
        return termCloseService.closeTerm(id);
    }
}
