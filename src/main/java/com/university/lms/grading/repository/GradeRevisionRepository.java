package com.university.lms.grading.repository;

import com.university.lms.grading.domain.GradeRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately {@link Repository}, not {@code JpaRepository}: this exposes {@code save} and
 * finders only, with no {@code delete*} or {@code deleteAll}, because a revision row is never
 * removed once written. There is no update method for the same reason a revision is never edited —
 * a correction is a new revision, not a rewritten one.
 */
public interface GradeRevisionRepository extends Repository<GradeRevision, UUID> {

    GradeRevision save(GradeRevision revision);

    List<GradeRevision> findByGradeIdOrderByRevisionNumberAsc(UUID gradeId);

    int countByGradeId(UUID gradeId);
}
