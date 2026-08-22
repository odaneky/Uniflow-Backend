package com.university.lms.grading.service;

import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.grading.api.GradeAppealActions;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.domain.GradingErrorCode;
import com.university.lms.grading.repository.GradeRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultGradeAppealActions implements GradeAppealActions {

    private final GradeRepository gradeRepository;

    public DefaultGradeAppealActions(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    @Override
    public void openAppeal(UUID gradeId, UUID actorUserId) {
        Grade grade = require(gradeId);
        grade.markUnderAppeal();
    }

    @Override
    public void resolveAppeal(UUID gradeId, UUID actorUserId) {
        Grade grade = require(gradeId);
        grade.clearUnderAppeal();
    }

    private Grade require(UUID gradeId) {
        return gradeRepository
                .findById(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        GradingErrorCode.GRADE_NOT_FOUND, "No grade exists with id " + gradeId));
    }
}
