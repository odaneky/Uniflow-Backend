package com.university.lms.grading.service;

import com.university.lms.grading.api.GradeDirectory;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.repository.GradeRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultGradeDirectory implements GradeDirectory {

    private final GradeRepository gradeRepository;

    public DefaultGradeDirectory(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    @Override
    public Optional<GradeSummary> findById(UUID gradeId) {
        return gradeRepository.findById(gradeId).map(DefaultGradeDirectory::toSummary);
    }

    @Override
    public boolean ownedByStudent(UUID gradeId, UUID studentId) {
        return gradeRepository
                .findById(gradeId)
                .map(grade -> grade.getStudentId().equals(studentId))
                .orElse(false);
    }

    static GradeSummary toSummary(Grade grade) {
        return new GradeSummary(
                grade.getId(),
                grade.getStudentId(),
                grade.getCourseSectionId(),
                grade.isPublished(),
                grade.isUnderAppeal());
    }
}
