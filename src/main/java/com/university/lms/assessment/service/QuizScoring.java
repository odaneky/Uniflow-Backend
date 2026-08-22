package com.university.lms.assessment.service;

import com.university.lms.assessment.domain.QuizScoringMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure scoring helpers for objective quiz items. */
public final class QuizScoring {

    private QuizScoring() {}

    public static BigDecimal scoreMultipleChoice(
            BigDecimal points, UUID selectedOptionId, UUID correctOptionId) {
        if (selectedOptionId == null || correctOptionId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return Objects.equals(selectedOptionId, correctOptionId)
                ? points.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scoreMultiSelect(
            BigDecimal points,
            QuizScoringMode mode,
            Set<UUID> selected,
            Set<UUID> correct) {
        Set<UUID> picked = selected == null ? Set.of() : selected;
        Set<UUID> right = correct == null ? Set.of() : correct;
        if (right.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        QuizScoringMode effective = mode == null ? QuizScoringMode.ALL_OR_NOTHING : mode;
        if (effective == QuizScoringMode.ALL_OR_NOTHING) {
            return picked.equals(right)
                    ? points.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // PARTIAL: zero if any incorrect option selected
        for (UUID id : picked) {
            if (!right.contains(id)) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
        }
        long correctSelected = picked.stream().filter(right::contains).count();
        if (correctSelected == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return points
                .multiply(BigDecimal.valueOf(correctSelected))
                .divide(BigDecimal.valueOf(right.size()), 2, RoundingMode.HALF_UP);
    }
}
