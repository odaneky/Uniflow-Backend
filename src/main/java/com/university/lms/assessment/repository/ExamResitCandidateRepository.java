package com.university.lms.assessment.repository;

import com.university.lms.assessment.domain.ExamResitCandidate;
import com.university.lms.assessment.domain.ExamResitCandidate.ExamResitCandidateId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamResitCandidateRepository extends JpaRepository<ExamResitCandidate, ExamResitCandidateId> {

    List<ExamResitCandidate> findByExamSittingIdOrderByAddedAtAsc(UUID examSittingId);

    boolean existsByExamSittingIdAndStudentId(UUID examSittingId, UUID studentId);

    /** Every sitting among these that has at least one candidate row — i.e. visibility-restricted. */
    @Query("select distinct c.examSittingId from ExamResitCandidate c where c.examSittingId in :sittingIds")
    Set<UUID> restrictedSittingIdsIn(@Param("sittingIds") Collection<UUID> sittingIds);

    /** Which of these restricted sittings this specific student is actually a candidate for. */
    @Query("select c.examSittingId from ExamResitCandidate c "
            + "where c.examSittingId in :sittingIds and c.studentId = :studentId")
    Set<UUID> sittingIdsCandidateFor(
            @Param("sittingIds") Collection<UUID> sittingIds, @Param("studentId") UUID studentId);
}
