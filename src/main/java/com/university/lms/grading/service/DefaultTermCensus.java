package com.university.lms.grading.service;

import com.university.lms.grading.api.TermCensus;
import com.university.lms.grading.domain.AcademicStanding;
import com.university.lms.grading.domain.TermAcademicRecord;
import com.university.lms.grading.repository.TermAcademicRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts {@code term_academic_records} to the published {@link TermCensus} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultTermCensus implements TermCensus {

    private final TermAcademicRecordRepository repository;

    public DefaultTermCensus(TermAcademicRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public Summary summarize(UUID academicTermId) {
        List<TermAcademicRecord> records = repository.findByAcademicTermId(academicTermId);

        int goodStanding = 0;
        int probation = 0;
        int creditsAttempted = 0;
        int creditsEarned = 0;
        BigDecimal gpaSum = BigDecimal.ZERO;
        int gpaCount = 0;
        List<PerStudentStanding> students = new java.util.ArrayList<>();

        for (TermAcademicRecord record : records) {
            if (record.getStanding() == AcademicStanding.PROBATION) {
                probation++;
            } else {
                goodStanding++;
            }
            creditsAttempted += record.getCreditsAttempted();
            creditsEarned += record.getCreditsEarned();
            if (record.getTermGpa() != null) {
                gpaSum = gpaSum.add(record.getTermGpa());
                gpaCount++;
            }
            students.add(new PerStudentStanding(
                    record.getStudentId(), record.getCumulativeGpa(), record.getCumulativeCreditsEarned()));
        }

        BigDecimal averageGpa =
                gpaCount == 0 ? null : gpaSum.divide(BigDecimal.valueOf(gpaCount), 2, RoundingMode.HALF_UP);

        return new Summary(
                academicTermId,
                records.size(),
                averageGpa,
                goodStanding,
                probation,
                creditsAttempted,
                creditsEarned,
                List.copyOf(students));
    }
}
