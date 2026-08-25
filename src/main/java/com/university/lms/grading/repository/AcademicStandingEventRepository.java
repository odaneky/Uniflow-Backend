package com.university.lms.grading.repository;

import com.university.lms.grading.domain.AcademicStandingEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately {@link Repository}, not {@code JpaRepository}: {@code save} and finders only, no
 * {@code delete*} — a standing event is never removed once written, the same reason {@link
 * com.university.lms.grading.repository.GradeRevisionRepository} takes this shape.
 */
public interface AcademicStandingEventRepository extends Repository<AcademicStandingEvent, UUID> {

    AcademicStandingEvent save(AcademicStandingEvent event);

    List<AcademicStandingEvent> findByStudentIdOrderByTermOrderAsc(UUID studentId);

    Optional<AcademicStandingEvent> findTopByStudentIdOrderByTermOrderDesc(UUID studentId);

    boolean existsByStudentIdAndAcademicTermId(UUID studentId, UUID academicTermId);
}
