package com.university.lms.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.assessment.domain.QuizScoringMode;
import com.university.lms.assessment.service.QuizScoring;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuizScoringTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID C = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Test
    void multipleChoiceAwardsFullOrZero() {
        assertThat(QuizScoring.scoreMultipleChoice(TEN, A, A)).isEqualByComparingTo("10.00");
        assertThat(QuizScoring.scoreMultipleChoice(TEN, B, A)).isEqualByComparingTo("0.00");
        assertThat(QuizScoring.scoreMultipleChoice(TEN, null, A)).isEqualByComparingTo("0.00");
    }

    @Test
    void multiSelectAllOrNothingRequiresExactSet() {
        Set<UUID> correct = Set.of(A, B);
        assertThat(QuizScoring.scoreMultiSelect(TEN, QuizScoringMode.ALL_OR_NOTHING, Set.of(A, B), correct))
                .isEqualByComparingTo("10.00");
        assertThat(QuizScoring.scoreMultiSelect(TEN, QuizScoringMode.ALL_OR_NOTHING, Set.of(A), correct))
                .isEqualByComparingTo("0.00");
        assertThat(QuizScoring.scoreMultiSelect(TEN, QuizScoringMode.ALL_OR_NOTHING, Set.of(A, B, C), correct))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void multiSelectPartialProportionalAndZerosOnWrongPick() {
        Set<UUID> correct = Set.of(A, B, C);
        assertThat(QuizScoring.scoreMultiSelect(TEN, QuizScoringMode.PARTIAL, Set.of(A, B), correct))
                .isEqualByComparingTo("6.67");
        assertThat(QuizScoring.scoreMultiSelect(
                        TEN, QuizScoringMode.PARTIAL, Set.of(A, B, UUID.randomUUID()), correct))
                .isEqualByComparingTo("0.00");
        assertThat(QuizScoring.scoreMultiSelect(TEN, QuizScoringMode.PARTIAL, Set.of(A, B, C), correct))
                .isEqualByComparingTo("10.00");
    }
}
