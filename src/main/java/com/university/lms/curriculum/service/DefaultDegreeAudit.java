package com.university.lms.curriculum.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultDegreeAudit implements DegreeAudit {

    private static final BigDecimal DEFAULT_MIN_GPA = BigDecimal.valueOf(2.0);

    private final CurriculumService curriculumService;
    private final AcademicStructure academicStructure;
    private final StudentDirectory studentDirectory;

    public DefaultDegreeAudit(
            CurriculumService curriculumService,
            AcademicStructure academicStructure,
            StudentDirectory studentDirectory) {
        this.curriculumService = curriculumService;
        this.academicStructure = academicStructure;
        this.studentDirectory = studentDirectory;
    }

    @Override
    public Eligibility eligibility(UUID studentId) {
        DegreeProgressResponse progress = curriculumService.progressOf(studentId);
        BigDecimal minGpa = minGraduationGpaFor(studentId);
        boolean certificateProgramme = studentDirectory
                .findById(studentId)
                .flatMap(student -> academicStructure.findProgramme(student.programmeId()))
                .map(p -> "CERTIFICATE".equals(p.programmeType()))
                .orElse(false);
        List<String> blockers = new ArrayList<>();
        if (progress.creditsEarned() < progress.creditsRequired()) {
            blockers.add(
                    "Credits earned " + progress.creditsEarned() + " of " + progress.creditsRequired() + " required");
        }
        if (!progress.remaining().isEmpty()) {
            blockers.add(progress.remaining().size() + " programme requirement(s) still outstanding");
        }
        if (!certificateProgramme && progress.gpa() != null && progress.gpa().compareTo(minGpa) < 0) {
            blockers.add("GPA below graduation minimum of " + minGpa);
        }
        boolean eligible = blockers.isEmpty();
        return new Eligibility(
                eligible,
                progress.creditsRequired(),
                progress.creditsEarned(),
                progress.gpa(),
                blockers);
    }

    private BigDecimal minGraduationGpaFor(UUID studentId) {
        return studentDirectory
                .findById(studentId)
                .flatMap(student -> academicStructure.findProgramme(student.programmeId()))
                .map(AcademicStructure.ProgrammeSummary::minGraduationGpa)
                .filter(gpa -> gpa != null)
                .orElse(DEFAULT_MIN_GPA);
    }
}
