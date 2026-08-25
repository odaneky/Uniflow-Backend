package com.university.lms.curriculum.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.curriculum.domain.CurriculumErrorCode;
import com.university.lms.curriculum.domain.DegreeAward;
import com.university.lms.curriculum.domain.GraduationClearanceItem;
import com.university.lms.curriculum.domain.GraduationClearanceStatus;
import com.university.lms.curriculum.domain.Honours;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.repository.DegreeAwardRepository;
import com.university.lms.curriculum.repository.GraduationClearanceItemRepository;
import com.university.lms.student.api.StudentDirectory;
import com.university.lms.student.api.StudentLifecycle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultDegreeAudit implements DegreeAudit {

    private static final BigDecimal DEFAULT_MIN_GPA = BigDecimal.valueOf(2.0);
    private static final BigDecimal SUMMA_CUM_LAUDE_GPA = new BigDecimal("3.90");
    private static final BigDecimal MAGNA_CUM_LAUDE_GPA = new BigDecimal("3.70");
    private static final BigDecimal CUM_LAUDE_GPA = new BigDecimal("3.50");

    private final CurriculumService curriculumService;
    private final GraduationClearanceItemRepository clearanceItemRepository;
    private final DegreeAwardRepository degreeAwardRepository;
    private final AcademicStructure academicStructure;
    private final StudentDirectory studentDirectory;
    private final StudentLifecycle studentLifecycle;

    public DefaultDegreeAudit(
            CurriculumService curriculumService,
            GraduationClearanceItemRepository clearanceItemRepository,
            DegreeAwardRepository degreeAwardRepository,
            AcademicStructure academicStructure,
            StudentDirectory studentDirectory,
            StudentLifecycle studentLifecycle) {
        this.curriculumService = curriculumService;
        this.clearanceItemRepository = clearanceItemRepository;
        this.degreeAwardRepository = degreeAwardRepository;
        this.academicStructure = academicStructure;
        this.studentDirectory = studentDirectory;
        this.studentLifecycle = studentLifecycle;
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
        for (GraduationClearanceItem item : clearanceItemRepository.findByStudentIdOrderByItemTypeAsc(studentId)) {
            if (item.getStatus() == GraduationClearanceStatus.PENDING) {
                blockers.add("Clearance pending: " + item.getItemType());
            }
        }
        curriculumService.residencyCreditsFor(studentId)
                .filter(required -> progress.creditsEarned() < required)
                .ifPresent(required -> blockers.add("Residency requirement not met: " + progress.creditsEarned()
                        + " of " + required + " required credits earned in residence"));
        boolean eligible = blockers.isEmpty();
        return new Eligibility(
                eligible,
                progress.creditsRequired(),
                progress.creditsEarned(),
                progress.gpa(),
                blockers);
    }

    @Override
    @Auditable(
            action = AuditTrail.Action.DEGREE_CONFERRED,
            entityType = AuditTrail.EntityType.STUDENT,
            entityId = "#studentId")
    @Transactional
    public void recordConferral(UUID studentId, UUID actorUserId) {
        StudentDirectory.StudentSummary student = studentDirectory
                .findById(studentId)
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        if (degreeAwardRepository.existsByStudentIdAndProgrammeId(studentId, student.programmeId())) {
            throw new ResourceAlreadyExistsException(
                    CurriculumErrorCode.DEGREE_ALREADY_CONFERRED,
                    "A degree has already been conferred for this student in this programme");
        }
        AcademicStructure.ProgrammeSummary programme = academicStructure
                .findProgramme(student.programmeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CurriculumErrorCode.PROGRAMME_NOT_FOUND,
                        "No programme exists with id " + student.programmeId()));
        DegreeProgressResponse progress = curriculumService.progressOf(studentId);
        boolean certificateProgramme = "CERTIFICATE".equals(programme.programmeType());
        Honours honours = certificateProgramme ? null : honoursFor(progress.gpa());
        degreeAwardRepository.save(new DegreeAward(
                studentId,
                student.programmeId(),
                student.curriculumVersionId(),
                programme.degreeAward(),
                LocalDate.now(),
                progress.gpa(),
                progress.creditsEarned(),
                honours,
                actorUserId));
        studentLifecycle.graduate(studentId, actorUserId);
    }

    private Honours honoursFor(BigDecimal gpa) {
        if (gpa == null) {
            return null;
        }
        if (gpa.compareTo(SUMMA_CUM_LAUDE_GPA) >= 0) {
            return Honours.SUMMA_CUM_LAUDE;
        }
        if (gpa.compareTo(MAGNA_CUM_LAUDE_GPA) >= 0) {
            return Honours.MAGNA_CUM_LAUDE;
        }
        if (gpa.compareTo(CUM_LAUDE_GPA) >= 0) {
            return Honours.CUM_LAUDE;
        }
        return null;
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
