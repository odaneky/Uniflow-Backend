package com.university.lms.disciplinary.repository;

import com.university.lms.disciplinary.domain.DisciplinaryCase;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisciplinaryCaseRepository extends JpaRepository<DisciplinaryCase, UUID> {

    List<DisciplinaryCase> findByStudentIdOrderByFiledAtDesc(UUID studentId);

    boolean existsByCaseNumber(String caseNumber);
}
