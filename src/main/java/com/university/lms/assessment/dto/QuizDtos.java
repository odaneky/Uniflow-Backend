package com.university.lms.assessment.dto;

import com.university.lms.assessment.domain.QuizQuestionType;
import com.university.lms.assessment.domain.QuizScoringMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class QuizDtos {

    private QuizDtos() {}

    public record QuizOptionRequest(
            UUID id,
            @NotBlank @Size(max = 1000) String label,
            boolean correct,
            Integer position) {}

    public record CreateQuestionRequest(
            @NotBlank @Size(max = 4000) String prompt,
            @NotNull QuizQuestionType questionType,
            @NotNull @DecimalMin("0.01") BigDecimal points,
            QuizScoringMode scoringMode,
            Boolean required,
            Integer position,
            List<QuizOptionRequest> options) {}

    public record UpdateQuestionRequest(
            @NotBlank @Size(max = 4000) String prompt,
            @NotNull @DecimalMin("0.01") BigDecimal points,
            QuizScoringMode scoringMode,
            Boolean required,
            Integer position,
            List<QuizOptionRequest> options) {}

    public record ReorderQuestionsRequest(@NotNull List<UUID> questionIds) {}

    public record UpdateAssessmentMetaRequest(
            @Size(max = 200) String title,
            @Size(max = 4000) String instructions,
            BigDecimal weightPercent,
            java.time.Instant dueAt,
            Integer durationMinutes,
            BigDecimal passMarkPercent,
            Boolean showCorrectAnswers) {}

    public record GradeQuestionRequest(
            @NotNull @DecimalMin("0.00") BigDecimal manualScore, @Size(max = 2000) String feedback) {}

    public record AnswerDraft(
            @NotNull UUID questionId,
            String textResponse,
            List<UUID> selectedOptionIds,
            UUID documentId) {}

    public record SaveAnswersRequest(@NotNull List<AnswerDraft> answers) {}

    public record QuizOptionView(UUID id, int position, String label, Boolean correct) {}

    public record QuizQuestionView(
            UUID id,
            int position,
            String prompt,
            QuizQuestionType questionType,
            BigDecimal points,
            QuizScoringMode scoringMode,
            boolean required,
            List<QuizOptionView> options) {}

    public record QuizStructureResponse(
            UUID assessmentId,
            String title,
            String instructions,
            boolean published,
            BigDecimal maxScore,
            BigDecimal weightPercent,
            java.time.Instant dueAt,
            Integer durationMinutes,
            BigDecimal passMarkPercent,
            boolean showCorrectAnswers,
            boolean structureLocked,
            List<QuizQuestionView> questions) {}

    public record QuizOverviewResponse(
            UUID assessmentId,
            UUID courseSectionId,
            String title,
            String instructions,
            String assessmentType,
            BigDecimal maxScore,
            BigDecimal weightPercent,
            java.time.Instant dueAt,
            Integer durationMinutes,
            BigDecimal passMarkPercent,
            boolean showCorrectAnswers,
            int questionCount,
            boolean examBlocked,
            boolean hasInProgressAttempt,
            boolean hasSubmittedAttempt,
            BigDecimal latestScore) {}

    public record QuizAnswerView(
            UUID questionId,
            String textResponse,
            List<UUID> selectedOptionIds,
            UUID documentId,
            String fileName,
            BigDecimal autoScore,
            BigDecimal manualScore,
            String feedback,
            /** When reveal is on for objective items: true full marks, false zero, null partial/pending. */
            Boolean correct) {}

    public record QuizAttemptDetailResponse(
            UUID attemptId,
            UUID assessmentId,
            UUID studentId,
            String studentNumber,
            String fullName,
            int attemptNumber,
            String status,
            java.time.Instant submittedAt,
            BigDecimal rawScore,
            BigDecimal maxScore,
            boolean pendingReview,
            boolean showCorrectAnswers,
            List<QuizQuestionView> questions,
            List<QuizAnswerView> answers) {}
}
