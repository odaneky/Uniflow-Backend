package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.QuizDtos.CreateQuestionRequest;
import com.university.lms.assessment.dto.QuizDtos.GradeQuestionRequest;
import com.university.lms.assessment.dto.QuizDtos.QuizAttemptDetailResponse;
import com.university.lms.assessment.dto.QuizDtos.QuizQuestionView;
import com.university.lms.assessment.dto.QuizDtos.QuizStructureResponse;
import com.university.lms.assessment.dto.QuizDtos.ReorderQuestionsRequest;
import com.university.lms.assessment.dto.QuizDtos.UpdateAssessmentMetaRequest;
import com.university.lms.assessment.dto.QuizDtos.UpdateQuestionRequest;
import com.university.lms.assessment.service.QuizService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff quiz structure and review. */
@RestController
@RequestMapping("/api/v1/assessments/{assessmentId}/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping
    public QuizStructureResponse structure(@PathVariable UUID assessmentId) {
        return quizService.structureForStaff(assessmentId);
    }

    @AccessClass(STAFF_ONLY)
    @PutMapping
    public QuizStructureResponse updateMeta(
            @PathVariable UUID assessmentId, @Valid @RequestBody UpdateAssessmentMetaRequest request) {
        return quizService.updateMeta(assessmentId, request);
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @AccessClass(STAFF_ONLY)
    public QuizQuestionView addQuestion(
            @PathVariable UUID assessmentId, @Valid @RequestBody CreateQuestionRequest request) {
        return quizService.addQuestion(assessmentId, request);
    }

    @AccessClass(STAFF_ONLY)
    @PutMapping("/questions/{questionId}")
    public QuizQuestionView updateQuestion(
            @PathVariable UUID assessmentId,
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        return quizService.updateQuestion(assessmentId, questionId, request);
    }

    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AccessClass(STAFF_ONLY)
    public void deleteQuestion(@PathVariable UUID assessmentId, @PathVariable UUID questionId) {
        quizService.deleteQuestion(assessmentId, questionId);
    }

    @AccessClass(STAFF_ONLY)
    @PutMapping("/reorder")
    public QuizStructureResponse reorder(
            @PathVariable UUID assessmentId, @Valid @RequestBody ReorderQuestionsRequest request) {
        return quizService.reorder(assessmentId, request);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/attempts/{attemptId}")
    public QuizAttemptDetailResponse attemptDetail(
            @PathVariable UUID assessmentId, @PathVariable UUID attemptId) {
        return quizService.attemptDetailForStaff(assessmentId, attemptId);
    }

    @PostMapping("/attempts/{attemptId}/questions/{questionId}/grade")
    @AccessClass(STAFF_ONLY)
    public QuizAttemptDetailResponse grade(
            @PathVariable UUID assessmentId,
            @PathVariable UUID attemptId,
            @PathVariable UUID questionId,
            @Valid @RequestBody GradeQuestionRequest request) {
        return quizService.gradeQuestion(assessmentId, attemptId, questionId, request);
    }
}
