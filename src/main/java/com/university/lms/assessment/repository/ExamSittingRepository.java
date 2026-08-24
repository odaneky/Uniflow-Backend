package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.ExamSitting;
import com.university.lms.assessment.domain.ExamSittingStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSittingRepository extends JpaRepository<ExamSitting, UUID> {

    List<ExamSitting> findByCourseSectionIdOrderByStartsAtAsc(UUID courseSectionId);

    /**
     * The student view: published sittings for the sections they are enrolled in, soonest first.
     *
     * <p>Unpublished rows are excluded in the query rather than filtered afterwards — a draft
     * timetable is wrong for most of its life, and a row that is never loaded cannot be leaked by a
     * later change to the response mapping.
     */
    List<ExamSitting> findByCourseSectionIdInAndPublishedTrueAndStatusOrderByStartsAtAsc(
            Collection<UUID> courseSectionIds, ExamSittingStatus status);

    /**
     * Everything booked into one hall, for double-booking checks.
     *
     * <p>Narrowed by room in the query. Loading every sitting in the university to validate one
     * booking is how a save button becomes a performance incident — a hall holds a handful of
     * exams, the table holds thousands.
     *
     * <p>Derived rather than hand-written JPQL, so Spring Data types the parameter itself; a bare
     * parameter inside {@code lower()} is the shape that once produced {@code lower(bytea)} here.
     */
    List<ExamSitting> findByRoomIgnoreCase(String room);

    /** The examinations office view: every sitting for these sections, drafts included. */
    List<ExamSitting> findByCourseSectionIdInOrderByStartsAtAsc(Collection<UUID> courseSectionIds);
}
