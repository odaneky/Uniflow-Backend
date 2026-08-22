package com.university.lms.finance.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.finance.api.StudentBilling.TuitionQuote;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.ProgrammeTuitionRate;
import com.university.lms.finance.domain.TuitionSchedule;
import com.university.lms.finance.dto.ReplaceProgrammeTuitionRateRequest;
import com.university.lms.finance.dto.ReplaceTuitionScheduleRequest;
import com.university.lms.finance.dto.TuitionScheduleResponse;
import com.university.lms.finance.repository.ProgrammeTuitionRateRepository;
import com.university.lms.finance.repository.TuitionScheduleRepository;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TuitionScheduleService {

    private final TuitionScheduleRepository scheduleRepository;
    private final ProgrammeTuitionRateRepository rateRepository;
    private final AcademicStructure academicStructure;
    private final StudentDirectory studentDirectory;

    public TuitionScheduleService(
            TuitionScheduleRepository scheduleRepository,
            ProgrammeTuitionRateRepository rateRepository,
            AcademicStructure academicStructure,
            StudentDirectory studentDirectory) {
        this.scheduleRepository = scheduleRepository;
        this.rateRepository = rateRepository;
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
                        .toList());
    }

    public TuitionQuote quoteFor(UUID studentId) {
        TuitionSchedule schedule = requireSchedule();
        BigDecimal perCredit = schedule.getAmountPerCredit();
        if (studentId != null) {
            UUID programmeId = studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::programmeId).orElse(null);
            if (programmeId != null) {
                perCredit = rateRepository
                        .findByProgrammeId(programmeId)
                        .map(ProgrammeTuitionRate::getAmountPerCredit)
                        .orElse(perCredit);
            }
        }
        return new TuitionQuote(perCredit, schedule.getCampusFee(), List.of());
    }

    @Transactional
    public TuitionScheduleResponse replace(ReplaceTuitionScheduleRequest request) {
        TuitionSchedule schedule = requireSchedule();
        schedule.replace(request.amountPerCredit(), request.campusFee());
        return find();
    }

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

    @Transactional
    public TuitionScheduleResponse clearProgrammeRate(UUID programmeId) {
        rateRepository.deleteByProgrammeId(programmeId);
        return find();
    }

    private TuitionSchedule requireSchedule() {
        return scheduleRepository
                .findById(TuitionSchedule.SINGLETON_ID)
                .orElseGet(() -> scheduleRepository.save(new TuitionSchedule(
                        TuitionSchedule.DEFAULT_PER_CREDIT, TuitionSchedule.DEFAULT_CAMPUS_FEE)));
    }
}
