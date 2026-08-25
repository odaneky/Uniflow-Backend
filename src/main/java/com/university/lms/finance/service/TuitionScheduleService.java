package com.university.lms.finance.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.finance.api.StudentBilling.TuitionQuote;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.ProgrammeTuitionRate;
import com.university.lms.finance.domain.ResidencyTuitionRate;
import com.university.lms.finance.domain.TuitionSchedule;
import com.university.lms.finance.dto.ReplaceProgrammeTuitionRateRequest;
import com.university.lms.finance.dto.ReplaceResidencyTuitionRateRequest;
import com.university.lms.finance.dto.ReplaceTuitionScheduleRequest;
import com.university.lms.finance.dto.TuitionScheduleResponse;
import com.university.lms.finance.repository.ProgrammeTuitionRateRepository;
import com.university.lms.finance.repository.ResidencyTuitionRateRepository;
import com.university.lms.finance.repository.TuitionScheduleRepository;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TuitionScheduleService {

    private final TuitionScheduleRepository scheduleRepository;
    private final ProgrammeTuitionRateRepository rateRepository;
    private final ResidencyTuitionRateRepository residencyRateRepository;
    private final AcademicStructure academicStructure;
    private final StudentDirectory studentDirectory;

    public TuitionScheduleService(
            TuitionScheduleRepository scheduleRepository,
            ProgrammeTuitionRateRepository rateRepository,
            ResidencyTuitionRateRepository residencyRateRepository,
            AcademicStructure academicStructure,
            StudentDirectory studentDirectory) {
        this.scheduleRepository = scheduleRepository;
        this.rateRepository = rateRepository;
        this.residencyRateRepository = residencyRateRepository;
        this.academicStructure = academicStructure;
        this.studentDirectory = studentDirectory;
    }

    public TuitionScheduleResponse find() {
        TuitionSchedule schedule = requireSchedule();
        return new TuitionScheduleResponse(
                schedule.getAmountPerCredit(),
                schedule.getCampusFee(),
                rateRepository.findAll().stream()
                        .map(row -> new TuitionScheduleResponse.ProgrammeRate(row.getProgrammeId(), row.getAmountPerCredit()))
                        .toList(),
                residencyRateRepository.findAll().stream()
                        .map(row -> new TuitionScheduleResponse.ResidencyRate(
                                row.getResidencyClassification(), row.getAmountPerCredit()))
                        .toList());
    }

    /**
     * A programme override wins when both are configured — some programmes cost more regardless
     * of where the student lives. Otherwise the student's residency tier sets the rate; the
     * institution default is the last resort.
     */
    public TuitionQuote quoteFor(UUID studentId) {
        TuitionSchedule schedule = requireSchedule();
        BigDecimal perCredit = schedule.getAmountPerCredit();
        if (studentId != null) {
            StudentDirectory.StudentSummary summary =
                    studentDirectory.findById(studentId).orElse(null);
            if (summary != null) {
                Optional<BigDecimal> programmeRate = summary.programmeId() == null
                        ? Optional.empty()
                        : rateRepository
                                .findByProgrammeId(summary.programmeId())
                                .map(ProgrammeTuitionRate::getAmountPerCredit);
                if (programmeRate.isPresent()) {
                    perCredit = programmeRate.get();
                } else if (summary.residencyClassification() != null) {
                    perCredit = residencyRateRepository
                            .findByResidencyClassification(summary.residencyClassification().name())
                            .map(ResidencyTuitionRate::getAmountPerCredit)
                            .orElse(perCredit);
                }
            }
        }
        return new TuitionQuote(perCredit, schedule.getCampusFee(), List.of());
    }

    @Auditable(
            action = AuditTrail.Action.TUITION_SCHEDULE_REPLACED,
            entityType = AuditTrail.EntityType.TUITION_SCHEDULE,
            entityId = "null",
            details = "'Base tuition schedule replaced'")
    @Transactional
    public TuitionScheduleResponse replace(ReplaceTuitionScheduleRequest request) {
        TuitionSchedule schedule = requireSchedule();
        schedule.replace(request.amountPerCredit(), request.campusFee());
        return find();
    }

    @Auditable(
            action = AuditTrail.Action.TUITION_SCHEDULE_REPLACED,
            entityType = AuditTrail.EntityType.TUITION_SCHEDULE,
            entityId = "#programmeId",
            details = "'Programme tuition rate replaced'")
    @Transactional
    public TuitionScheduleResponse replaceProgrammeRate(UUID programmeId, ReplaceProgrammeTuitionRateRequest request) {
        if (!academicStructure.programmeExists(programmeId)) {
            throw new ResourceNotFoundException(
                    FinanceErrorCode.TUITION_PROGRAMME_NOT_FOUND, "No programme exists with id " + programmeId);
        }
        ProgrammeTuitionRate row = rateRepository
                .findByProgrammeId(programmeId)
                .orElseGet(() -> new ProgrammeTuitionRate(programmeId, request.amountPerCredit()));
        row.replace(request.amountPerCredit());
        rateRepository.save(row);
        return find();
    }

    @Auditable(
            action = AuditTrail.Action.TUITION_SCHEDULE_REPLACED,
            entityType = AuditTrail.EntityType.TUITION_SCHEDULE,
            entityId = "#programmeId",
            details = "'Programme tuition rate cleared'")
    @Transactional
    public TuitionScheduleResponse clearProgrammeRate(UUID programmeId) {
        rateRepository.deleteByProgrammeId(programmeId);
        return find();
    }

    @Auditable(
            action = AuditTrail.Action.TUITION_SCHEDULE_REPLACED,
            entityType = AuditTrail.EntityType.TUITION_SCHEDULE,
            entityId = "null",
            details = "'Residency tuition rate replaced for ' + #residencyClassification.name()")
    @Transactional
    public TuitionScheduleResponse replaceResidencyRate(
            ResidencyClassification residencyClassification, ReplaceResidencyTuitionRateRequest request) {
        String key = residencyClassification.name();
        ResidencyTuitionRate row = residencyRateRepository
                .findByResidencyClassification(key)
                .orElseGet(() -> new ResidencyTuitionRate(key, request.amountPerCredit()));
        row.replace(request.amountPerCredit());
        residencyRateRepository.save(row);
        return find();
    }

    @Auditable(
            action = AuditTrail.Action.TUITION_SCHEDULE_REPLACED,
            entityType = AuditTrail.EntityType.TUITION_SCHEDULE,
            entityId = "null",
            details = "'Residency tuition rate cleared for ' + #residencyClassification.name()")
    @Transactional
    public TuitionScheduleResponse clearResidencyRate(ResidencyClassification residencyClassification) {
        residencyRateRepository.deleteByResidencyClassification(residencyClassification.name());
        return find();
    }

    private TuitionSchedule requireSchedule() {
        return scheduleRepository
                .findById(TuitionSchedule.SINGLETON_ID)
                .orElseGet(() -> scheduleRepository.save(new TuitionSchedule(
                        TuitionSchedule.DEFAULT_PER_CREDIT, TuitionSchedule.DEFAULT_CAMPUS_FEE)));
    }
}
