package com.university.lms.academic.service;

import com.university.lms.academic.domain.AcademicErrorCode;
import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.academic.domain.AcademicYear;
import com.university.lms.academic.dto.AcademicTermResponse;
import com.university.lms.academic.dto.AcademicYearResponse;
import com.university.lms.academic.dto.AddDropWindowRequest;
import com.university.lms.academic.dto.CreateAcademicTermRequest;
import com.university.lms.academic.dto.CreateAcademicYearRequest;
import com.university.lms.academic.dto.RegistrationWindowRequest;
import com.university.lms.academic.repository.AcademicTermRepository;
import com.university.lms.academic.repository.AcademicYearRepository;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the academic calendar: years and the terms within them.
 *
 * <p>Date ordering is validated here rather than being left to the database CHECK constraints.
 * Both are present, but the constraint would surface as a generic
 * {@code DATA_INTEGRITY_VIOLATION} that tells the caller nothing about which date was wrong; the
 * constraint's job is to stop bad data arriving by any other route.
 */
@Service
@Transactional(readOnly = true)
public class AcademicCalendarService {

    private static final Logger log = LoggerFactory.getLogger(AcademicCalendarService.class);

    private final AcademicYearRepository academicYearRepository;
    private final AcademicTermRepository academicTermRepository;

    public AcademicCalendarService(
            AcademicYearRepository academicYearRepository, AcademicTermRepository academicTermRepository) {
        this.academicYearRepository = academicYearRepository;
        this.academicTermRepository = academicTermRepository;
    }

    // ------------------------------------------------------------------
    // Years
    // ------------------------------------------------------------------

    @Transactional
    public AcademicYearResponse createYear(CreateAcademicYearRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        requireOrderedDates(request.startDate(), request.endDate());

        if (academicYearRepository.existsByCode(code)) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.ACADEMIC_YEAR_CODE_ALREADY_EXISTS,
                    "Academic year " + code + " already exists");
        }

        try {
            AcademicYear saved = academicYearRepository.saveAndFlush(
                    new AcademicYear(code, request.startDate(), request.endDate()));
            log.info("Created academic year {} ({})", saved.getCode(), saved.getId());
            return AcademicYearResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.ACADEMIC_YEAR_CODE_ALREADY_EXISTS,
                    "Academic year " + code + " already exists",
                    ex);
        }
    }

    public PageResponse<AcademicYearResponse> findYears(Pageable pageable) {
        return PageResponse.from(academicYearRepository.findAll(pageable), AcademicYearResponse::from);
    }

    public AcademicYearResponse findYear(UUID yearId) {
        return AcademicYearResponse.from(requireYear(yearId));
    }

    // ------------------------------------------------------------------
    // Terms
    // ------------------------------------------------------------------

    @Transactional
    public AcademicTermResponse createTerm(CreateAcademicTermRequest request) {
        AcademicYear year = requireYear(request.academicYearId());
        requireOrderedDates(request.startDate(), request.endDate());

        if (academicTermRepository.existsByAcademicYearIdAndSequenceNumber(
                year.getId(), request.sequenceNumber())) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.ACADEMIC_TERM_SEQUENCE_ALREADY_EXISTS,
                    "Term " + request.sequenceNumber() + " already exists for " + year.getCode());
        }

        AcademicTerm term = new AcademicTerm(
                year,
                request.name(),
                request.termType(),
                request.sequenceNumber(),
                request.startDate(),
                request.endDate());

        // Both ends or neither — a half-open window is ambiguous exactly when it matters.
        if (request.registrationOpensAt() != null || request.registrationClosesAt() != null) {
            applyWindow(term, request.registrationOpensAt(), request.registrationClosesAt());
        }

        try {
            AcademicTerm saved = academicTermRepository.saveAndFlush(term);
            log.info("Created term {} for year {}", saved.getName(), year.getCode());
            return AcademicTermResponse.from(saved, Instant.now());
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.ACADEMIC_TERM_SEQUENCE_ALREADY_EXISTS,
                    "Term " + request.sequenceNumber() + " already exists for " + year.getCode(),
                    ex);
        }
    }

    public List<AcademicTermResponse> findTermsOfYear(UUID yearId) {
        requireYear(yearId);
        Instant now = Instant.now();
        return academicTermRepository.findByAcademicYearIdOrderBySequenceNumberAsc(yearId).stream()
                .map(term -> AcademicTermResponse.from(term, now))
                .toList();
    }

    public AcademicTermResponse findTerm(UUID termId) {
        return AcademicTermResponse.from(requireTerm(termId), Instant.now());
    }

    /**
     * Opens or moves a term's registration window.
     *
     * <p>Exposed as its own action rather than a general update: opening registration is the single
     * most consequential switch in the system — it is what allows students to start competing for
     * seats — and it deserves to be an explicit, individually auditable operation.
     */
    @Transactional
    public AcademicTermResponse setRegistrationWindow(UUID termId, RegistrationWindowRequest request) {
        AcademicTerm term = requireTerm(termId);
        applyWindow(term, request.opensAt(), request.closesAt());
        log.info("Registration window for term {} set to {} .. {}", termId, request.opensAt(), request.closesAt());
        return AcademicTermResponse.from(term, Instant.now());
    }

    @Transactional
    public AcademicTermResponse setAddDropWindow(UUID termId, AddDropWindowRequest request) {
        AcademicTerm term = requireTerm(termId);
        if (!request.closesAt().isAfter(request.opensAt())) {
            throw new ValidationException(
                    AcademicErrorCode.INVALID_DATE_RANGE, "add/drop closesAt must be after opensAt");
        }
        term.openAddDrop(request.opensAt(), request.closesAt(), request.tuitionDueOn());
        log.info("Add/drop window for term {} set to {} .. {}", termId, request.opensAt(), request.closesAt());
        return AcademicTermResponse.from(term, Instant.now());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void applyWindow(AcademicTerm term, Instant opensAt, Instant closesAt) {
        if (opensAt == null || closesAt == null) {
            throw new ValidationException(
                    AcademicErrorCode.INVALID_DATE_RANGE,
                    "Both registrationOpensAt and registrationClosesAt must be supplied together");
        }
        if (!closesAt.isAfter(opensAt)) {
            throw new ValidationException(
                    AcademicErrorCode.INVALID_DATE_RANGE, "registrationClosesAt must be after registrationOpensAt");
        }
        term.openRegistration(opensAt, closesAt);
    }

    private void requireOrderedDates(LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new ValidationException(AcademicErrorCode.INVALID_DATE_RANGE, "endDate must be after startDate");
        }
    }

    private AcademicYear requireYear(UUID id) {
        return academicYearRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AcademicErrorCode.ACADEMIC_YEAR_NOT_FOUND, "No academic year exists with id " + id));
    }

    private AcademicTerm requireTerm(UUID id) {
        return academicTermRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AcademicErrorCode.ACADEMIC_TERM_NOT_FOUND, "No academic term exists with id " + id));
    }
}
