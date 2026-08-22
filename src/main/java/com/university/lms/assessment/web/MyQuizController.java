package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.AttemptResponse;
import com.university.lms.assessment.dto.QuizDtos.QuizAnswerView;
import com.university.lms.assessment.dto.QuizDtos.QuizAttemptDetailResponse;
import com.university.lms.assessment.dto.QuizDtos.QuizOverviewResponse;
import com.university.lms.assessment.dto.QuizDtos.SaveAnswersRequest;
import com.university.lms.assessment.service.QuizService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Student quiz attempt lifecycle. */
@RestController
@RequestMapping("/api/v1/me/assessments/{assessmentId}")
public class MyQuizController {

    private final QuizService quizService;

    public MyQuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/quiz")
    public QuizOverviewResponse overview(@PathVariable UUID assessmentId) {
        return quizService.overviewForStudent(assessmentId);
    }

    @PostMapping("/attempts")
    public AttemptResponse startOrResume(@PathVariable UUID assessmentId) {
        return quizService.startOrResume(assessmentId);
    }

    @GetMapping("/attempts/{attemptId}")
    public QuizAttemptDetailResponse detail(
            @PathVariable UUID assessmentId, @PathVariable UUID attemptId) {
        return quizService.attemptDetailForStudent(assessmentId, attemptId);
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public QuizAttemptDetailResponse saveAnswers(
            @PathVariable UUID assessmentId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SaveAnswersRequest request) {
        return quizService.saveAnswers(assessmentId, attemptId, request);
    }

    @PostMapping(
            value = "/attempts/{attemptId}/questions/{questionId}/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QuizAnswerView uploadFile(
            @PathVariable UUID assessmentId,
            @PathVariable UUID attemptId,
            @PathVariable UUID questionId,
            @RequestParam("file") MultipartFile file) {
        return quizService.uploadAnswerFile(assessmentId, attemptId, questionId, file);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public QuizAttemptDetailResponse submit(
            @PathVariable UUID assessmentId, @PathVariable UUID attemptId) {
        return quizService.submit(assessmentId, attemptId);
    }
}
