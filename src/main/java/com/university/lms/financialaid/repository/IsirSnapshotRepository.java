package com.university.lms.financialaid.repository;

import com.university.lms.financialaid.domain.IsirSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IsirSnapshotRepository extends JpaRepository<IsirSnapshot, UUID> {

    Optional<IsirSnapshot> findByStudentIdAndAidYear(UUID studentId, String aidYear);

    List<IsirSnapshot> findByStudentIdOrderByAidYearDesc(UUID studentId);
}
