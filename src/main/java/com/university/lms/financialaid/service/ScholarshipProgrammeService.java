package com.university.lms.financialaid.service;

import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.domain.ScholarshipProgramme;
import com.university.lms.financialaid.dto.CreateScholarshipProgrammeRequest;
import com.university.lms.financialaid.dto.ScholarshipProgrammeResponse;
import com.university.lms.financialaid.dto.UpdateScholarshipProgrammeRequest;
import com.university.lms.financialaid.repository.ScholarshipProgrammeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** E9: the catalog of named scholarship funds a student can be awarded from. */
@Service
@Transactional(readOnly = true)
public class ScholarshipProgrammeService {

    private final ScholarshipProgrammeRepository repository;

    public ScholarshipProgrammeService(ScholarshipProgrammeRepository repository) {
        this.repository = repository;
    }

    public List<ScholarshipProgrammeResponse> findAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(ScholarshipProgrammeResponse::from)
                .toList();
    }

    @Auditable(
            action = AuditTrail.Action.SCHOLARSHIP_PROGRAMME_CREATED,
            entityType = AuditTrail.EntityType.SCHOLARSHIP_PROGRAMME,
            entityId = "#result.id()",
            details = "#result.name()")
    @Transactional
    public ScholarshipProgrammeResponse create(CreateScholarshipProgrammeRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ResourceAlreadyExistsException(
                    FinancialAidErrorCode.SCHOLARSHIP_PROGRAMME_NAME_ALREADY_EXISTS,
                    "A scholarship programme named \"" + name + "\" already exists");
        }
        ScholarshipProgramme saved = repository.save(new ScholarshipProgramme(
                name,
                blankToNull(request.sponsorName()),
                blankToNull(request.description()),
                request.defaultAmount(),
                request.renewable(),
                request.maxRenewals(),
                blankToNull(request.eligibilityCriteria())));
        return ScholarshipProgrammeResponse.from(saved);
    }

    @Auditable(
            action = AuditTrail.Action.SCHOLARSHIP_PROGRAMME_UPDATED,
            entityType = AuditTrail.EntityType.SCHOLARSHIP_PROGRAMME,
            entityId = "#programmeId",
            details = "#result.name()")
    @Transactional
    public ScholarshipProgrammeResponse update(UUID programmeId, UpdateScholarshipProgrammeRequest request) {
        ScholarshipProgramme programme = require(programmeId);
        String name = request.name() == null ? programme.getName() : request.name().trim();
        if (!name.equalsIgnoreCase(programme.getName()) && repository.existsByNameIgnoreCase(name)) {
            throw new ResourceAlreadyExistsException(
                    FinancialAidErrorCode.SCHOLARSHIP_PROGRAMME_NAME_ALREADY_EXISTS,
                    "A scholarship programme named \"" + name + "\" already exists");
        }
        String sponsorName = Boolean.TRUE.equals(request.clearSponsorName())
                ? null
                : request.sponsorName() != null ? request.sponsorName().trim() : programme.getSponsorName();
        Integer maxRenewals = Boolean.TRUE.equals(request.clearMaxRenewals())
                ? null
                : request.maxRenewals() != null ? request.maxRenewals() : programme.getMaxRenewals();
        programme.replace(
                name,
                sponsorName,
                request.description() == null ? programme.getDescription() : blankToNull(request.description()),
                request.defaultAmount() == null ? programme.getDefaultAmount() : request.defaultAmount(),
                request.renewable() == null ? programme.isRenewable() : request.renewable(),
                maxRenewals,
                request.eligibilityCriteria() == null
                        ? programme.getEligibilityCriteria()
                        : blankToNull(request.eligibilityCriteria()),
                request.active() == null ? programme.isActive() : request.active());
        return ScholarshipProgrammeResponse.from(programme);
    }

    @Auditable(
            action = AuditTrail.Action.SCHOLARSHIP_PROGRAMME_DEACTIVATED,
            entityType = AuditTrail.EntityType.SCHOLARSHIP_PROGRAMME,
            entityId = "#programmeId")
    @Transactional
    public void deactivate(UUID programmeId) {
        require(programmeId).deactivate();
    }

    ScholarshipProgramme require(UUID programmeId) {
        return repository
                .findById(programmeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.SCHOLARSHIP_PROGRAMME_NOT_FOUND,
                        "No scholarship programme exists with id " + programmeId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
