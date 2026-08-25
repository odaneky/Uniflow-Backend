package com.university.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.finance.api.StudentBilling.TuitionQuote;
import com.university.lms.finance.domain.ProgrammeTuitionRate;
import com.university.lms.finance.domain.ResidencyTuitionRate;
import com.university.lms.finance.domain.TuitionSchedule;
import com.university.lms.finance.repository.ProgrammeTuitionRateRepository;
import com.university.lms.finance.repository.ResidencyTuitionRateRepository;
import com.university.lms.finance.repository.TuitionScheduleRepository;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * E5: {@code students.residency_classification} was added in V53 with a comment saying it drives
 * the tuition tier, but no Java code ever read it. These tests pin the precedence a tuition quote
 * must now follow: a programme override still wins (some programmes cost more regardless of where
 * the student lives), otherwise the student's residency tier sets the rate, and the institution
 * default is the last resort.
 */
@ExtendWith(MockitoExtension.class)
class TuitionScheduleServiceTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();

    @Mock
    private TuitionScheduleRepository scheduleRepository;

    @Mock
    private ProgrammeTuitionRateRepository rateRepository;

    @Mock
    private ResidencyTuitionRateRepository residencyRateRepository;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private StudentDirectory studentDirectory;

    @InjectMocks
    private TuitionScheduleService service;

    @BeforeEach
    void setUp() {
        when(scheduleRepository.findById(TuitionSchedule.SINGLETON_ID))
                .thenReturn(Optional.of(new TuitionSchedule(new BigDecimal("200.00"), new BigDecimal("350.00"))));
    }

    private void studentIs(ResidencyClassification residency) {
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, UUID.randomUUID(), "20260001", PROGRAMME_ID, null, true, residency)));
    }

    @Test
    @DisplayName("no residency rate configured falls back to the institution default")
    void noResidencyRateFallsBackToInstitutionDefault() {
        studentIs(ResidencyClassification.OUT_OF_STATE);
        when(rateRepository.findByProgrammeId(PROGRAMME_ID)).thenReturn(Optional.empty());
        when(residencyRateRepository.findByResidencyClassification("OUT_OF_STATE")).thenReturn(Optional.empty());

        TuitionQuote quote = service.quoteFor(STUDENT_ID);

        assertThat(quote.amountPerCredit()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("an out-of-state student is charged the out-of-state residency rate")
    void residencyRateAppliesWhenNoProgrammeOverride() {
        studentIs(ResidencyClassification.OUT_OF_STATE);
        when(rateRepository.findByProgrammeId(PROGRAMME_ID)).thenReturn(Optional.empty());
        when(residencyRateRepository.findByResidencyClassification("OUT_OF_STATE"))
                .thenReturn(Optional.of(new ResidencyTuitionRate("OUT_OF_STATE", new BigDecimal("650.00"))));

        TuitionQuote quote = service.quoteFor(STUDENT_ID);

        assertThat(quote.amountPerCredit()).isEqualByComparingTo("650.00");
    }

    @Test
    @DisplayName("an in-district student is not charged the out-of-state rate")
    void inDistrictStudentGetsTheInDistrictRate() {
        studentIs(ResidencyClassification.IN_DISTRICT);
        when(rateRepository.findByProgrammeId(PROGRAMME_ID)).thenReturn(Optional.empty());
        when(residencyRateRepository.findByResidencyClassification("IN_DISTRICT"))
                .thenReturn(Optional.of(new ResidencyTuitionRate("IN_DISTRICT", new BigDecimal("120.00"))));

        TuitionQuote quote = service.quoteFor(STUDENT_ID);

        assertThat(quote.amountPerCredit()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("a programme override wins over the residency rate")
    void programmeOverrideWinsOverResidencyRate() {
        studentIs(ResidencyClassification.OUT_OF_STATE);
        when(rateRepository.findByProgrammeId(PROGRAMME_ID))
                .thenReturn(Optional.of(new ProgrammeTuitionRate(PROGRAMME_ID, new BigDecimal("900.00"))));

        TuitionQuote quote = service.quoteFor(STUDENT_ID);

        assertThat(quote.amountPerCredit()).isEqualByComparingTo("900.00");
    }
}
