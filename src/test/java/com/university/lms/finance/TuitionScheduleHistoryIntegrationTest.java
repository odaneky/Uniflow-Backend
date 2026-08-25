package com.university.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.academic.domain.Programme;
import com.university.lms.finance.domain.ProgrammeTuitionRate;
import com.university.lms.finance.domain.TuitionSchedule;
import com.university.lms.finance.dto.ReplaceProgrammeTuitionRateRequest;
import com.university.lms.finance.dto.ReplaceTuitionScheduleRequest;
import com.university.lms.finance.repository.ProgrammeTuitionRateRepository;
import com.university.lms.finance.repository.TuitionScheduleRepository;
import com.university.lms.finance.service.TuitionScheduleService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * C8: {@code tuition_schedules} and {@code programme_tuition_rates} used to be overwritten in
 * place — replacing a rate destroyed the only record of what it used to be. These prove the
 * replacement instead closes the open row and opens a new one, including the same-day case that
 * caught a real ordering bug in the analogous curriculum-version publish/retire flow.
 */
class TuitionScheduleHistoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TuitionScheduleService tuitionScheduleService;

    @Autowired
    private TuitionScheduleRepository tuitionScheduleRepository;

    @Autowired
    private ProgrammeTuitionRateRepository programmeTuitionRateRepository;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Test
    @DisplayName("replacing the institution schedule twice in one day closes the first row rather than overwriting it")
    void replacingTheScheduleTwiceKeepsHistory() throws Exception {
        long before = tuitionScheduleRepository.count();

        RunAs.staff(() -> tuitionScheduleService.replace(
                new ReplaceTuitionScheduleRequest(new BigDecimal("210.00"), new BigDecimal("360.00"))));
        RunAs.staff(() -> tuitionScheduleService.replace(
                new ReplaceTuitionScheduleRequest(new BigDecimal("220.00"), new BigDecimal("370.00"))));

        // Two new rows landed — the first replace's row was closed, not deleted or rewritten.
        assertThat(tuitionScheduleRepository.count()).isEqualTo(before + 2);

        List<TuitionSchedule> open = tuitionScheduleRepository.findAll().stream()
                .filter(row -> row.getEffectiveTo() == null)
                .toList();
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getAmountPerCredit()).isEqualByComparingTo("220.00");

        long closedRows = tuitionScheduleRepository.findAll().stream()
                .filter(row -> row.getEffectiveTo() != null)
                .count();
        assertThat(closedRows).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("clearing a programme rate closes it rather than deleting it, and history survives a second replace")
    void programmeRateHistorySurvivesReplaceAndClear() throws Exception {
        Programme programme = academicFixtures.programme();

        RunAs.staff(() -> tuitionScheduleService.replaceProgrammeRate(
                programme.getId(), new ReplaceProgrammeTuitionRateRequest(new BigDecimal("500.00"))));
        RunAs.staff(() -> tuitionScheduleService.replaceProgrammeRate(
                programme.getId(), new ReplaceProgrammeTuitionRateRequest(new BigDecimal("600.00"))));

        assertThat(programmeTuitionRateRepository
                        .findByProgrammeIdAndEffectiveToIsNull(programme.getId())
                        .orElseThrow()
                        .getAmountPerCredit())
                .isEqualByComparingTo("600.00");

        RunAs.staff(() -> tuitionScheduleService.clearProgrammeRate(programme.getId()));

        assertThat(programmeTuitionRateRepository.findByProgrammeIdAndEffectiveToIsNull(programme.getId()))
                .isEmpty();

        List<ProgrammeTuitionRate> history = programmeTuitionRateRepository.findAll().stream()
                .filter(row -> row.getProgrammeId().equals(programme.getId()))
                .toList();
        assertThat(history).hasSize(2);
        assertThat(history).allSatisfy(row -> assertThat(row.getEffectiveTo()).isNotNull());
    }
}
