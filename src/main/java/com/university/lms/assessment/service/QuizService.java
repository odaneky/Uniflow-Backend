package com.university.lms.assessment.service;

import com.university.lms.assessment.domain.Assessment;
import com.university.lms.assessment.domain.AssessmentAttempt;
import com.university.lms.assessment.domain.AssessmentErrorCode;
import com.university.lms.assessment.domain.AssessmentType;
import com.university.lms.assessment.domain.AttemptStatus;
import com.university.lms.assessment.domain.QuizAnswer;
import com.university.lms.assessment.domain.QuizOption;
import com.university.lms.assessment.domain.QuizQuestion;
import com.university.lms.assessment.domain.QuizQuestionType;
import com.university.lms.assessment.domain.QuizScoringMode;
import com.university.lms.assessment.dto.AttemptResponse;
import com.university.lms.assessment.dto.QuizDtos.AnswerDraft;
import com.university.lms.assessment.dto.QuizDtos.CreateQuestionRequest;
import com.university.lms.assessment.dto.QuizDtos.GradeQuestionRequest;
import com.university.lms.assessment.dto.QuizDtos.QuizAnswerView;
import com.university.lms.assessment.dto.QuizDtos.QuizAttemptDetailResponse;
import com.university.lms.assessment.dto.QuizDtos.QuizOptionRequest;
import com.university.lms.assessment.dto.QuizDtos.QuizOptionView;
import com.university.lms.assessment.dto.QuizDtos.QuizQuestionView;
import com.university.lms.assessment.dto.QuizDtos.QuizOverviewResponse;
import com.university.lms.assessment.dto.QuizDtos.QuizStructureResponse;
import com.university.lms.assessment.dto.QuizDtos.ReorderQuestionsRequest;
import com.university.lms.assessment.dto.QuizDtos.SaveAnswersRequest;
import com.university.lms.assessment.dto.QuizDtos.UpdateAssessmentMetaRequest;
import com.university.lms.assessment.dto.QuizDtos.UpdateQuestionRequest;
import com.university.lms.assessment.repository.AssessmentAttemptRepository;
import com.university.lms.assessment.repository.AssessmentRepository;
import com.university.lms.assessment.repository.QuizAnswerRepository;
import com.university.lms.assessment.repository.QuizOptionRepository;
import com.university.lms.assessment.repository.QuizQuestionRepository;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class QuizService {

    private static final long MAX_UPLOAD_BYTES = 12_582_912L;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAnswerRepository answerRepository;
    private final CourseCatalog courseCatalog;
    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final DocumentStore documentStore;
    private final CurrentUserProvider currentUserProvider;
    private final StudentBilling studentBilling;

    public QuizService(
            AssessmentRepository assessmentRepository,
            AssessmentAttemptRepository attemptRepository,
            QuizQuestionRepository questionRepository,
            QuizOptionRepository optionRepository,
            QuizAnswerRepository answerRepository,
            CourseCatalog courseCatalog,
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            DocumentStore documentStore,
            CurrentUserProvider currentUserProvider,
            StudentBilling studentBilling) {
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.answerRepository = answerRepository;
        this.courseCatalog = courseCatalog;
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.documentStore = documentStore;
        this.currentUserProvider = currentUserProvider;
        this.studentBilling = studentBilling;
    }

    public QuizStructureResponse structureForStaff(UUID assessmentId) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        return toStructure(assessment, true);
    }

    public QuizOverviewResponse overviewForStudent(UUID assessmentId) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        UUID studentId = requireEnrolledStudent(assessment.getCourseSectionId());
        if (!assessment.isPublished()) {
            throw notFound(assessmentId);
        }
        boolean examBlocked = false;
        AssessmentType type = assessment.getAssessmentType();
        if (type == AssessmentType.EXAM || type == AssessmentType.QUIZ) {
            UUID termId = courseCatalog
                    .findSection(assessment.getCourseSectionId())
                    .map(CourseCatalog.SectionSummary::academicTermId)
                    .orElse(null);
            examBlocked = studentBilling
                    .standingOf(studentId, termId, java.time.LocalDate.now(java.time.ZoneOffset.UTC))
                    .examBlocked();
        }
        List<AssessmentAttempt> attempts =
                attemptRepository.findByAssessmentIdAndStudentIdOrderByAttemptNumberAsc(assessmentId, studentId);
        boolean inProgress = attempts.stream().anyMatch(a -> a.getStatus() == AttemptStatus.IN_PROGRESS);
        boolean submitted = attempts.stream()
                .anyMatch(a -> a.getStatus() == AttemptStatus.SUBMITTED
                        || a.getStatus() == AttemptStatus.LATE
                        || a.getStatus() == AttemptStatus.GRADED);
        BigDecimal latestScore = attempts.stream()
                .filter(a -> a.getRawScore() != null)
                .reduce((a, b) -> a.getAttemptNumber() >= b.getAttemptNumber() ? a : b)
                .map(AssessmentAttempt::getRawScore)
                .orElse(null);
        int questionCount = (int) questionRepository.countByAssessmentId(assessmentId);
        return new QuizOverviewResponse(
                assessment.getId(),
                assessment.getCourseSectionId(),
                assessment.getTitle(),
                assessment.getInstructions(),
                assessment.getAssessmentType().name(),
                assessment.getMaxScore(),
                assessment.getWeightPercent(),
                assessment.getDueAt(),
                assessment.getDurationMinutes(),
                assessment.getPassMarkPercent(),
                assessment.isShowCorrectAnswers(),
                questionCount,
                examBlocked,
                inProgress,
                submitted,
                latestScore);
    }

    @Transactional
    public QuizStructureResponse updateMeta(UUID assessmentId, UpdateAssessmentMetaRequest request) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        if (request.title() != null && !request.title().isBlank()) {
            assessment.retitle(request.title().trim());
        }
        if (request.instructions() != null) {
            assessment.describe(request.instructions());
        }
        if (request.weightPercent() != null) {
            assessment.reweight(request.weightPercent());
        }
        if (request.dueAt() != null) {
            assessment.schedule(request.dueAt());
        }
        if (request.durationMinutes() != null) {
            assessment.setDurationMinutes(request.durationMinutes() <= 0 ? null : request.durationMinutes());
        }
        if (request.passMarkPercent() != null) {
            assessment.setPassMarkPercent(request.passMarkPercent());
        }
        if (request.showCorrectAnswers() != null) {
            assessment.setShowCorrectAnswers(request.showCorrectAnswers());
        }
        return toStructure(assessment, true);
    }

    @Transactional
    public QuizQuestionView addQuestion(UUID assessmentId, CreateQuestionRequest request) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        refuseIfStructureLocked(assessmentId);
        validateQuestionShape(request.questionType(), request.scoringMode(), request.options());
        int position = request.position() != null
                ? request.position()
                : questionRepository.findByAssessmentIdOrderByPositionAsc(assessmentId).size() + 1;
        QuizQuestion question = new QuizQuestion(
                assessment, position, request.prompt().trim(), request.questionType(), request.points());
        if (request.questionType() == QuizQuestionType.MULTI_SELECT) {
            question.setScoringMode(
                    request.scoringMode() == null ? QuizScoringMode.ALL_OR_NOTHING : request.scoringMode());
        }
        if (request.required() != null) {
            question.setRequired(request.required());
        }
        questionRepository.save(question);
        replaceOptions(question, request.options());
        recomputeMaxScore(assessment);
        return toQuestionView(question, true);
    }

    @Transactional
    public QuizQuestionView updateQuestion(UUID assessmentId, UUID questionId, UpdateQuestionRequest request) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        refuseIfStructureLocked(assessmentId);
        QuizQuestion question = requireQuestion(assessmentId, questionId);
        question.reword(request.prompt().trim());
        question.setPoints(request.points());
        if (request.position() != null) {
            question.moveTo(request.position());
        }
        if (request.required() != null) {
            question.setRequired(request.required());
        }
        if (question.getQuestionType() == QuizQuestionType.MULTI_SELECT) {
            question.setScoringMode(
                    request.scoringMode() == null ? QuizScoringMode.ALL_OR_NOTHING : request.scoringMode());
        }
        if (request.options() != null
                && (question.getQuestionType() == QuizQuestionType.MULTIPLE_CHOICE
                        || question.getQuestionType() == QuizQuestionType.MULTI_SELECT)) {
            validateQuestionShape(question.getQuestionType(), question.getScoringMode(), request.options());
            replaceOptions(question, request.options());
        }
        recomputeMaxScore(assessment);
        return toQuestionView(question, true);
    }

    @Transactional
    public void deleteQuestion(UUID assessmentId, UUID questionId) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        refuseIfStructureLocked(assessmentId);
        QuizQuestion question = requireQuestion(assessmentId, questionId);
        optionRepository.deleteByQuestionId(questionId);
        questionRepository.delete(question);
        recomputeMaxScore(assessment);
    }

    @Transactional
    public QuizStructureResponse reorder(UUID assessmentId, ReorderQuestionsRequest request) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        requireTeacherOrAdmin(assessment.getCourseSectionId());
        refuseIfStructureLocked(assessmentId);
        Map<UUID, QuizQuestion> byId = questionRepository.findByAssessmentIdOrderByPositionAsc(assessmentId).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q));
        int i = 1;
        for (UUID id : request.questionIds()) {
            QuizQuestion q = byId.get(id);
            if (q == null) {
                throw new ResourceNotFoundException(
                        AssessmentErrorCode.QUIZ_QUESTION_NOT_FOUND, "No question exists with id " + id);
            }
            q.moveTo(i++);
        }
        return toStructure(assessment, true);
    }

    @Transactional
    public AttemptResponse startOrResume(UUID assessmentId) {
        Assessment assessment = requireQuizAssessment(assessmentId);
        UUID studentId = requireEnrolledStudent(assessment.getCourseSectionId());
        if (!assessment.isPublished()) {
            throw notFound(assessmentId);
        }
        refuseIfExamBlocked(assessment, studentId);
        List<AssessmentAttempt> existing =
                attemptRepository.findByAssessmentIdAndStudentIdOrderByAttemptNumberAsc(assessmentId, studentId);
        for (AssessmentAttempt attempt : existing) {
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                return toAttemptResponse(attempt);
            }
        }
        int next = existing.stream().mapToInt(AssessmentAttempt::getAttemptNumber).max().orElse(0) + 1;
        AssessmentAttempt attempt = new AssessmentAttempt(assessment, studentId, next);
        attemptRepository.save(attempt);
        return toAttemptResponse(attempt);
    }

    public QuizAttemptDetailResponse attemptDetailForStudent(UUID assessmentId, UUID attemptId) {
        AssessmentAttempt attempt = requireAttempt(attemptId, assessmentId);
        UUID studentId = requireEnrolledStudent(attempt.getAssessment().getCourseSectionId());
        if (!attempt.getStudentId().equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        if (!attempt.getAssessment().isPublished()) {
            throw notFound(assessmentId);
        }
        return toAttemptDetail(attempt, revealCorrectForStudent(attempt));
    }

    public QuizAttemptDetailResponse attemptDetailForStaff(UUID assessmentId, UUID attemptId) {
        AssessmentAttempt attempt = requireAttempt(attemptId, assessmentId);
        requireTeacherOrAdmin(attempt.getAssessment().getCourseSectionId());
        return toAttemptDetail(attempt, true);
    }

    @Transactional
    public QuizAttemptDetailResponse saveAnswers(UUID assessmentId, UUID attemptId, SaveAnswersRequest request) {
        AssessmentAttempt attempt = requireOwnInProgress(assessmentId, attemptId);
        Map<UUID, QuizQuestion> questions = questionRepository
                .findByAssessmentIdOrderByPositionAsc(assessmentId)
                .stream()
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q));
        for (AnswerDraft draft : request.answers()) {
            QuizQuestion question = questions.get(draft.questionId());
            if (question == null) {
                throw new BusinessException(
                        AssessmentErrorCode.QUIZ_ANSWER_INVALID, "Unknown question " + draft.questionId());
            }
            QuizAnswer answer = answerRepository
                    .findByAttemptIdAndQuestionId(attemptId, draft.questionId())
                    .orElseGet(() -> answerRepository.save(new QuizAnswer(attempt, question)));
            applyDraft(answer, question, draft);
        }
        return toAttemptDetail(attempt, false);
    }

    @Transactional
    public QuizAnswerView uploadAnswerFile(
            UUID assessmentId, UUID attemptId, UUID questionId, MultipartFile file) {
        AssessmentAttempt attempt = requireOwnInProgress(assessmentId, attemptId);
        QuizQuestion question = requireQuestion(assessmentId, questionId);
        if (question.getQuestionType() != QuizQuestionType.FILE_UPLOAD) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_ANSWER_INVALID, "Question does not accept a file");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(AssessmentErrorCode.ATTEMPT_FILE_REQUIRED, "A file is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(
                    AssessmentErrorCode.ATTEMPT_FILE_TOO_LARGE, "File must be at most 12 MB");
        }
        String originalName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        if (!allowedFile(file.getContentType(), originalName)) {
            throw new BusinessException(
                    AssessmentErrorCode.ATTEMPT_FILE_TYPE_NOT_ALLOWED, "Only PDF or ZIP files are accepted");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BusinessException(AssessmentErrorCode.ATTEMPT_FILE_REQUIRED, "A file is required");
        }
        CurrentUser caller = currentUserProvider.require();
        DocumentStore.StoredFile stored = documentStore.store(
                caller.userId(),
                "QUIZ_ANSWER",
                originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                bytes);
        QuizAnswer answer = answerRepository
                .findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseGet(() -> answerRepository.save(new QuizAnswer(attempt, question)));
        answer.attachDocument(stored.id());
        return toAnswerView(answer, true, false, null);
    }

    @Transactional
    public QuizAttemptDetailResponse submit(UUID assessmentId, UUID attemptId) {
        AssessmentAttempt attempt = requireOwnInProgress(assessmentId, attemptId);
        List<QuizQuestion> questions = questionRepository.findByAssessmentIdOrderByPositionAsc(assessmentId);
        Map<UUID, List<QuizOption>> optionsByQuestion = optionsGrouped(assessmentId);
        for (QuizQuestion question : questions) {
            if (!question.isRequired()) {
                continue;
            }
            QuizAnswer answer = answerRepository
                    .findByAttemptIdAndQuestionId(attemptId, question.getId())
                    .orElse(null);
            if (!hasResponse(question, answer)) {
                throw new BusinessException(
                        AssessmentErrorCode.QUIZ_ANSWER_INVALID,
                        "Required question is unanswered: " + question.getId());
            }
        }
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean pending = false;
        for (QuizQuestion question : questions) {
            QuizAnswer answer = answerRepository
                    .findByAttemptIdAndQuestionId(attemptId, question.getId())
                    .orElse(null);
            if (answer == null) {
                if (isManual(question.getQuestionType())) {
                    pending = true;
                }
                continue;
            }
            List<QuizOption> options = optionsByQuestion.getOrDefault(question.getId(), List.of());
            BigDecimal auto = autoGrade(question, options, answer);
            if (auto != null) {
                answer.setAutoScore(auto);
                total = total.add(auto);
            } else {
                pending = true;
            }
        }
        attempt.submit(Instant.now());
        if (!pending) {
            attempt.recordScore(total);
        } else {
            attempt.recordPartialScore(total);
        }
        return toAttemptDetail(attempt, revealCorrectForStudent(attempt));
    }

    @Transactional
    public QuizAttemptDetailResponse gradeQuestion(
            UUID assessmentId, UUID attemptId, UUID questionId, GradeQuestionRequest request) {
        AssessmentAttempt attempt = requireAttempt(attemptId, assessmentId);
        requireTeacherOrAdmin(attempt.getAssessment().getCourseSectionId());
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_ATTEMPT_NOT_IN_PROGRESS, "Attempt has not been submitted");
        }
        QuizQuestion question = requireQuestion(assessmentId, questionId);
        if (!isManual(question.getQuestionType())) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_GRADE_NOT_MANUAL, "Only short-answer and file items are graded manually");
        }
        if (request.manualScore().compareTo(question.getPoints()) > 0) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_ANSWER_INVALID, "Score cannot exceed question points");
        }
        QuizAnswer answer = answerRepository
                .findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseGet(() -> answerRepository.save(new QuizAnswer(attempt, question)));
        answer.gradeManually(request.manualScore(), request.feedback());
        recomputeAttemptScore(attempt);
        return toAttemptDetail(attempt, true);
    }

    private void recomputeAttemptScore(AssessmentAttempt attempt) {
        List<QuizQuestion> questions =
                questionRepository.findByAssessmentIdOrderByPositionAsc(attempt.getAssessment().getId());
        List<QuizAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        Map<UUID, QuizAnswer> byQ =
                answers.stream().collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean pending = false;
        for (QuizQuestion question : questions) {
            QuizAnswer answer = byQ.get(question.getId());
            BigDecimal effective = answer == null ? null : answer.effectiveScore();
            if (effective != null) {
                total = total.add(effective);
            } else if (isManual(question.getQuestionType())) {
                pending = true;
            } else if (answer != null && answer.getAutoScore() == null) {
                pending = true;
            }
        }
        if (pending) {
            attempt.recordPartialScore(total);
        } else {
            attempt.recordScore(total);
        }
    }

    private void applyDraft(QuizAnswer answer, QuizQuestion question, AnswerDraft draft) {
        switch (question.getQuestionType()) {
            case SHORT_ANSWER -> answer.setTextResponse(draft.textResponse());
            case MULTIPLE_CHOICE -> {
                List<UUID> selected = draft.selectedOptionIds() == null ? List.of() : draft.selectedOptionIds();
                if (selected.size() > 1) {
                    throw new BusinessException(
                            AssessmentErrorCode.QUIZ_ANSWER_INVALID, "Multiple choice accepts one option");
                }
                answer.replaceSelections(new HashSet<>(selected));
            }
            case MULTI_SELECT -> answer.replaceSelections(
                    draft.selectedOptionIds() == null ? Set.of() : new HashSet<>(draft.selectedOptionIds()));
            case FILE_UPLOAD -> {
                if (draft.documentId() != null) {
                    answer.attachDocument(draft.documentId());
                }
            }
        }
    }

    private static boolean hasResponse(QuizQuestion question, QuizAnswer answer) {
        if (answer == null) {
            return false;
        }
        return switch (question.getQuestionType()) {
            case SHORT_ANSWER -> answer.getTextResponse() != null && !answer.getTextResponse().isBlank();
            case MULTIPLE_CHOICE, MULTI_SELECT -> !answer.getSelectedOptionIds().isEmpty();
            case FILE_UPLOAD -> answer.getDocumentId() != null;
        };
    }

    private static boolean isManual(QuizQuestionType type) {
        return type == QuizQuestionType.SHORT_ANSWER || type == QuizQuestionType.FILE_UPLOAD;
    }

    private BigDecimal autoGrade(QuizQuestion question, List<QuizOption> options, QuizAnswer answer) {
        return switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE -> {
                UUID correct = options.stream()
                        .filter(QuizOption::isCorrect)
                        .map(QuizOption::getId)
                        .findFirst()
                        .orElse(null);
                UUID selected = answer.getSelectedOptionIds().stream().findFirst().orElse(null);
                yield QuizScoring.scoreMultipleChoice(question.getPoints(), selected, correct);
            }
            case MULTI_SELECT -> {
                Set<UUID> correct = options.stream()
                        .filter(QuizOption::isCorrect)
                        .map(QuizOption::getId)
                        .collect(Collectors.toSet());
                yield QuizScoring.scoreMultiSelect(
                        question.getPoints(), question.getScoringMode(), answer.getSelectedOptionIds(), correct);
            }
            case SHORT_ANSWER, FILE_UPLOAD -> null;
        };
    }

    private QuizStructureResponse toStructure(Assessment assessment, boolean includeCorrect) {
        List<QuizQuestionView> questions = questionRepository
                .findByAssessmentIdOrderByPositionAsc(assessment.getId())
                .stream()
                .map(q -> toQuestionView(q, includeCorrect))
                .toList();
        return new QuizStructureResponse(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getInstructions(),
                assessment.isPublished(),
                assessment.getMaxScore(),
                assessment.getWeightPercent(),
                assessment.getDueAt(),
                assessment.getDurationMinutes(),
                assessment.getPassMarkPercent(),
                assessment.isShowCorrectAnswers(),
                isStructureLocked(assessment.getId()),
                questions);
    }

    private QuizQuestionView toQuestionView(QuizQuestion question, boolean includeCorrect) {
        List<QuizOptionView> options = optionRepository.findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                .map(o -> new QuizOptionView(
                        o.getId(), o.getPosition(), o.getLabel(), includeCorrect ? o.isCorrect() : null))
                .toList();
        return new QuizQuestionView(
                question.getId(),
                question.getPosition(),
                question.getPrompt(),
                question.getQuestionType(),
                question.getPoints(),
                question.getScoringMode(),
                question.isRequired(),
                options);
    }

    private boolean revealCorrectForStudent(AssessmentAttempt attempt) {
        return attempt.getStatus() != AttemptStatus.IN_PROGRESS
                && attempt.getAssessment().isShowCorrectAnswers();
    }

    private QuizAttemptDetailResponse toAttemptDetail(AssessmentAttempt attempt, boolean includeCorrect) {
        Assessment assessment = attempt.getAssessment();
        List<QuizQuestionView> questions = questionRepository
                .findByAssessmentIdOrderByPositionAsc(assessment.getId())
                .stream()
                .map(q -> toQuestionView(q, includeCorrect))
                .toList();
        boolean showScores = includeCorrect || attempt.getStatus() != AttemptStatus.IN_PROGRESS;
        Map<UUID, QuizQuestionView> questionById =
                questions.stream().collect(Collectors.toMap(QuizQuestionView::id, q -> q));
        List<QuizAnswer> answers = answerRepository.findByAttemptIdWithQuestion(attempt.getId());
        List<QuizAnswerView> answerViews = answers.stream()
                .map(a -> toAnswerView(a, showScores, includeCorrect, questionById.get(a.getQuestion().getId())))
                .toList();
        boolean pending = questions.stream().anyMatch(q -> {
            if (q.questionType() != QuizQuestionType.SHORT_ANSWER
                    && q.questionType() != QuizQuestionType.FILE_UPLOAD) {
                return false;
            }
            QuizAnswerView a = answerViews.stream()
                    .filter(x -> x.questionId().equals(q.id()))
                    .findFirst()
                    .orElse(null);
            return a == null || a.manualScore() == null;
        });
        StudentDirectory.StudentSummary student = studentDirectory.findById(attempt.getStudentId()).orElse(null);
        String number = student == null ? null : student.studentNumber();
        String name = student == null
                ? null
                : userDirectory.findById(student.userId()).map(UserDirectory.UserSummary::fullName).orElse(null);
        return new QuizAttemptDetailResponse(
                attempt.getId(),
                assessment.getId(),
                attempt.getStudentId(),
                number,
                name,
                attempt.getAttemptNumber(),
                attempt.getStatus().name(),
                attempt.getSubmittedAt(),
                attempt.getRawScore(),
                assessment.getMaxScore(),
                pending && attempt.getStatus() != AttemptStatus.IN_PROGRESS,
                includeCorrect,
                questions,
                answerViews);
    }

    private QuizAnswerView toAnswerView(
            QuizAnswer answer, boolean showScores, boolean revealCorrect, QuizQuestionView question) {
        String fileName = null;
        if (answer.getDocumentId() != null) {
            fileName = documentStore.find(answer.getDocumentId()).map(DocumentStore.StoredFile::fileName).orElse(null);
        }
        Boolean correct = null;
        if (revealCorrect && question != null) {
            correct = outcomeFor(question, answer);
        }
        return new QuizAnswerView(
                answer.getQuestion().getId(),
                answer.getTextResponse(),
                List.copyOf(answer.getSelectedOptionIds()),
                answer.getDocumentId(),
                fileName,
                showScores ? answer.getAutoScore() : null,
                showScores ? answer.getManualScore() : null,
                showScores ? answer.getFeedback() : null,
                correct);
    }

    private static Boolean outcomeFor(QuizQuestionView question, QuizAnswer answer) {
        QuizQuestionType type = question.questionType();
        if (type == QuizQuestionType.SHORT_ANSWER || type == QuizQuestionType.FILE_UPLOAD) {
            if (answer.getManualScore() == null) {
                return null;
            }
            return answer.getManualScore().compareTo(question.points()) == 0;
        }
        BigDecimal score = answer.getAutoScore();
        if (score == null) {
            return null;
        }
        int cmp = score.compareTo(question.points());
        if (cmp == 0) {
            return true;
        }
        if (score.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        return null; // partial credit
    }

    private AttemptResponse toAttemptResponse(AssessmentAttempt attempt) {
        StudentDirectory.StudentSummary student = studentDirectory.findById(attempt.getStudentId()).orElse(null);
        String number = student == null ? null : student.studentNumber();
        String name = student == null
                ? null
                : userDirectory.findById(student.userId()).map(UserDirectory.UserSummary::fullName).orElse(null);
        return AttemptResponse.from(attempt, number, name, null, null);
    }

    private void replaceOptions(QuizQuestion question, List<QuizOptionRequest> options) {
        optionRepository.deleteByQuestionId(question.getId());
        if (options == null) {
            return;
        }
        int pos = 1;
        for (QuizOptionRequest opt : options) {
            int p = opt.position() != null ? opt.position() : pos++;
            optionRepository.save(new QuizOption(question, p, opt.label().trim(), opt.correct()));
        }
    }

    private void validateQuestionShape(
            QuizQuestionType type, QuizScoringMode scoringMode, List<QuizOptionRequest> options) {
        if (type == QuizQuestionType.MULTI_SELECT && scoringMode == null) {
            // default applied later
        }
        if (type == QuizQuestionType.MULTIPLE_CHOICE || type == QuizQuestionType.MULTI_SELECT) {
            if (options == null || options.size() < 2) {
                throw new BusinessException(
                        AssessmentErrorCode.QUIZ_INVALID_QUESTION, "Choice questions need at least two options");
            }
            long correct = options.stream().filter(QuizOptionRequest::correct).count();
            if (type == QuizQuestionType.MULTIPLE_CHOICE && correct != 1) {
                throw new BusinessException(
                        AssessmentErrorCode.QUIZ_INVALID_QUESTION, "Multiple choice needs exactly one correct option");
            }
            if (type == QuizQuestionType.MULTI_SELECT && correct < 1) {
                throw new BusinessException(
                        AssessmentErrorCode.QUIZ_INVALID_QUESTION, "Multi-select needs at least one correct option");
            }
        } else if (options != null && !options.isEmpty()) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_INVALID_QUESTION, "This question type does not take options");
        }
    }

    private void recomputeMaxScore(Assessment assessment) {
        BigDecimal sum = questionRepository.findByAssessmentIdOrderByPositionAsc(assessment.getId()).stream()
                .map(QuizQuestion::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            sum = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        assessment.setMaxScore(sum);
    }

    private Map<UUID, List<QuizOption>> optionsGrouped(UUID assessmentId) {
        Map<UUID, List<QuizOption>> map = new HashMap<>();
        for (QuizOption option : optionRepository.findByQuestion_Assessment_Id(assessmentId)) {
            map.computeIfAbsent(option.getQuestion().getId(), k -> new ArrayList<>()).add(option);
        }
        return map;
    }

    private boolean isStructureLocked(UUID assessmentId) {
        return attemptRepository.findByAssessmentIdWithAssessment(assessmentId).stream()
                .anyMatch(a -> a.getStatus() == AttemptStatus.SUBMITTED
                        || a.getStatus() == AttemptStatus.LATE
                        || a.getStatus() == AttemptStatus.GRADED);
    }

    private void refuseIfStructureLocked(UUID assessmentId) {
        if (isStructureLocked(assessmentId)) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_STRUCTURE_LOCKED,
                    "Quiz structure cannot change after a submitted attempt");
        }
    }

    private AssessmentAttempt requireOwnInProgress(UUID assessmentId, UUID attemptId) {
        AssessmentAttempt attempt = requireAttempt(attemptId, assessmentId);
        UUID studentId = requireEnrolledStudent(attempt.getAssessment().getCourseSectionId());
        if (!attempt.getStudentId().equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_ATTEMPT_NOT_IN_PROGRESS, "Attempt is not in progress");
        }
        refuseIfExamBlocked(attempt.getAssessment(), studentId);
        return attempt;
    }

    private AssessmentAttempt requireAttempt(UUID attemptId, UUID assessmentId) {
        AssessmentAttempt attempt = attemptRepository
                .findByIdWithAssessment(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No attempt exists with id " + attemptId));
        if (!attempt.getAssessment().getId().equals(assessmentId)) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.ATTEMPT_NOT_FOUND, "No attempt exists with id " + attemptId);
        }
        return attempt;
    }

    private QuizQuestion requireQuestion(UUID assessmentId, UUID questionId) {
        QuizQuestion question = questionRepository
                .findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.QUIZ_QUESTION_NOT_FOUND, "No question exists with id " + questionId));
        if (!Objects.equals(question.getAssessment().getId(), assessmentId)) {
            throw new ResourceNotFoundException(
                    AssessmentErrorCode.QUIZ_QUESTION_NOT_FOUND, "No question exists with id " + questionId);
        }
        return question;
    }

    private Assessment requireQuizAssessment(UUID assessmentId) {
        Assessment assessment = assessmentRepository
                .findById(assessmentId)
                .orElseThrow(() -> notFound(assessmentId));
        if (!assessment.isQuizLike()) {
            throw new BusinessException(
                    AssessmentErrorCode.QUIZ_NOT_QUIZ_TYPE, "Assessment is not a quiz or exam");
        }
        return assessment;
    }

    private static ResourceNotFoundException notFound(UUID assessmentId) {
        return new ResourceNotFoundException(
                AssessmentErrorCode.ASSESSMENT_NOT_FOUND, "No assessment exists with id " + assessmentId);
    }

    private UUID requireEnrolledStudent(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        requireKnownSection(sectionId);
        UUID studentId = studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
        if (!enrollmentDirectory.canAccessLearning(studentId, sectionId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        return studentId;
    }

    private void refuseIfExamBlocked(Assessment assessment, UUID studentId) {
        AssessmentType type = assessment.getAssessmentType();
        if (type != AssessmentType.EXAM && type != AssessmentType.QUIZ) {
            return;
        }
        UUID termId = courseCatalog
                .findSection(assessment.getCourseSectionId())
                .map(CourseCatalog.SectionSummary::academicTermId)
                .orElse(null);
        if (studentBilling
                .standingOf(studentId, termId, java.time.LocalDate.now(java.time.ZoneOffset.UTC))
                .examBlocked()) {
            throw new BusinessException(
                    AssessmentErrorCode.ASSESSMENT_EXAM_BLOCKED,
                    "Exams are blocked until the required tuition installment is paid");
        }
    }

    private static boolean allowedFile(String contentType, String fileName) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return type.contains("pdf")
                || type.contains("zip")
                || name.endsWith(".pdf")
                || name.endsWith(".zip");
    }

    private void requireKnownSection(UUID sectionId) {
        courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ASSESSMENT_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
    }

    private void requireTeacherOrAdmin(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)) {
            requireKnownSection(sectionId);
            return;
        }
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AssessmentErrorCode.ASSESSMENT_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
        if (caller.hasRole(SecurityRoles.LECTURER) && caller.userId().equals(section.lecturerUserId())) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to change this section");
    }
}
