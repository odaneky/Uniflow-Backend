package com.university.lms.curriculum.service;

import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultDegreeAudit implements DegreeAudit {

    private final CurriculumService curriculumService;

    public DefaultDegreeAudit(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @Override
    public Eligibility eligibility(UUID studentId) {
        DegreeProgressResponse progress = curriculumService.progressOf(studentId);
        List<String> blockers = new ArrayList<>();
        if (progress.creditsEarned() < progress.creditsRequired()) {
            blockers.add(
                    "Credits earned " + progress.creditsEarned() + " of " + progress.creditsRequired() + " required");
        }
        if (!progress.remaining().isEmpty()) {
            blockers.add(progress.remaining().size() + " programme requirement(s) still outstanding");
        }
        if (progress.gpa() != null && progress.gpa().compareTo(BigDecimal.valueOf(2.0)) < 0) {
            blockers.add("GPA below graduation minimum");
        }
        boolean eligible = blockers.isEmpty();
        return new Eligibility(
                eligible,
                progress.creditsRequired(),
                progress.creditsEarned(),
                progress.gpa(),
                blockers);
    }
}
