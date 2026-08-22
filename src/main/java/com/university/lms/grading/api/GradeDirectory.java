package com.university.lms.grading.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only grade facts for request validation and appeal routing. */
public interface GradeDirectory {

    record GradeSummary(UUID id, UUID studentId, UUID courseSectionId, boolean published, boolean underAppeal) {}

    Optional<GradeSummary> findById(UUID gradeId);

    boolean ownedByStudent(UUID gradeId, UUID studentId);
}
