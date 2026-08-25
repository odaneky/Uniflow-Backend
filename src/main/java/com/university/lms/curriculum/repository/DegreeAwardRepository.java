package com.university.lms.curriculum.repository;

import com.university.lms.curriculum.domain.DegreeAward;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DegreeAwardRepository extends JpaRepository<DegreeAward, UUID> {

    List<DegreeAward> findByStudentIdOrderByConferredOnDesc(UUID studentId);

    boolean existsByStudentIdAndProgrammeId(UUID studentId, UUID programmeId);
}
