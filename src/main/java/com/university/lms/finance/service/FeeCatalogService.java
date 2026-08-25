package com.university.lms.finance.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.finance.api.StudentBilling.CatalogFeeQuote;
import com.university.lms.finance.domain.FeeApplicability;
import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeCatalogItem;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.dto.CreateFeeRequest;
import com.university.lms.finance.dto.FeeResponse;
import com.university.lms.finance.dto.UpdateFeeRequest;
import com.university.lms.finance.repository.FeeCatalogRepository;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FeeCatalogService {

    private final FeeCatalogRepository feeRepository;
    private final AcademicStructure academicStructure;
    private final CourseCatalog courseCatalog;
    private final StudentDirectory studentDirectory;

    public FeeCatalogService(
            FeeCatalogRepository feeRepository,
            AcademicStructure academicStructure,
            CourseCatalog courseCatalog,
            StudentDirectory studentDirectory) {
        this.feeRepository = feeRepository;
        this.academicStructure = academicStructure;
        this.courseCatalog = courseCatalog;
        this.studentDirectory = studentDirectory;
    }

    public List<FeeResponse> findAll() {
        return feeRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    public List<CatalogFeeQuote> quotesFor(UUID studentId) {
        UUID programmeId = studentId == null
                ? null
                : studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::programmeId).orElse(null);
        return feeRepository.findAllByOrderByNameAsc().stream()
                .filter(fee -> FeeApplicability.matchesProgramme(fee, programmeId))
                .map(fee -> new CatalogFeeQuote(
                        fee.getId(),
                        fee.getName(),
                        fee.getKind().name(),
                        fee.getAssessment().name(),
                        fee.getAmount(),
                        fee.getCourseId(),
                        fee.getProgrammeId()))
                .toList();
    }

    @Auditable(
            action = AuditTrail.Action.FEE_CREATED,
            entityType = AuditTrail.EntityType.FEE,
            entityId = "#result.id()",
            details = "#result.name()")
    @Transactional
    public FeeResponse create(CreateFeeRequest request) {
        String name = request.name().trim();
        if (feeRepository.existsByNameIgnoreCase(name)) {
            throw new ResourceAlreadyExistsException(
                    FinanceErrorCode.FEE_NAME_ALREADY_EXISTS, "A fee named \"" + name + "\" already exists");
        }
        validateScope(request.assessment(), request.courseId(), request.programmeId());
        FeeCatalogItem saved = feeRepository.save(new FeeCatalogItem(
                name,
                descriptionOf(request.description()),
                request.amount(),
                request.kind(),
                request.assessment(),
                request.courseId(),
                request.programmeId()));
        return toResponse(saved);
    }

    @Auditable(
            action = AuditTrail.Action.FEE_UPDATED,
            entityType = AuditTrail.EntityType.FEE,
            entityId = "#feeId",
            details = "#result.name()")
    @Transactional
    public FeeResponse update(UUID feeId, UpdateFeeRequest request) {
        FeeCatalogItem fee = require(feeId);
        String name = request.name() == null ? fee.getName() : request.name().trim();
        if (!name.equalsIgnoreCase(fee.getName()) && feeRepository.existsByNameIgnoreCase(name)) {
            throw new ResourceAlreadyExistsException(
                    FinanceErrorCode.FEE_NAME_ALREADY_EXISTS, "A fee named \"" + name + "\" already exists");
        }
        FeeAssessment assessment = request.assessment() == null ? fee.getAssessment() : request.assessment();
        UUID courseId = Boolean.TRUE.equals(request.clearCourseId())
                ? null
                : request.courseId() != null ? request.courseId() : fee.getCourseId();
        UUID programmeId = Boolean.TRUE.equals(request.clearProgrammeId())
                ? null
                : request.programmeId() != null ? request.programmeId() : fee.getProgrammeId();
        if (assessment == FeeAssessment.ONCE_PER_TERM) {
            courseId = null;
        }
        validateScope(assessment, courseId, programmeId);
        fee.replace(
                name,
                request.description() == null ? fee.getDescription() : descriptionOf(request.description()),
                request.amount() == null ? fee.getAmount() : request.amount(),
                request.kind() == null ? fee.getKind() : request.kind(),
                assessment,
                courseId,
                programmeId,
                request.active() == null ? fee.isActive() : request.active());
        return toResponse(fee);
    }

    @Auditable(
            action = AuditTrail.Action.FEE_DEACTIVATED,
            entityType = AuditTrail.EntityType.FEE,
            entityId = "#feeId")
    @Transactional
    public void deactivate(UUID feeId) {
        require(feeId).deactivate();
    }

    private void validateScope(FeeAssessment assessment, UUID courseId, UUID programmeId) {
        if (courseId != null && assessment == FeeAssessment.ONCE_PER_TERM) {
            throw new ValidationException(
                    FinanceErrorCode.INVALID_FEE,
                    "A course-specific fee is charged per enrolment, not once per term");
        }
        if (courseId != null && !courseCatalog.courseExists(courseId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.INVALID_FEE, "No course exists with id " + courseId);
        }
        if (programmeId != null && !academicStructure.programmeExists(programmeId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.TUITION_PROGRAMME_NOT_FOUND, "No programme exists with id " + programmeId);
        }
    }

    private FeeResponse toResponse(FeeCatalogItem fee) {
        String courseCode = fee.getCourseId() == null
                ? null
                : courseCatalog.findCourse(fee.getCourseId()).map(CourseCatalog.CourseSummary::courseCode).orElse(null);
        String programmeCode = fee.getProgrammeId() == null
                ? null
                : academicStructure
                        .findProgramme(fee.getProgrammeId())
                        .map(AcademicStructure.ProgrammeSummary::code)
                        .orElse(null);
        return FeeResponse.from(fee, courseCode, programmeCode);
    }

    private FeeCatalogItem require(UUID feeId) {
        return feeRepository
                .findById(feeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinanceErrorCode.FEE_NOT_FOUND, "No fee exists with id " + feeId));
    }

    private static String descriptionOf(String description) {
        if (description == null || description.isBlank()) {
            return "—";
        }
        return description.trim();
    }
}
