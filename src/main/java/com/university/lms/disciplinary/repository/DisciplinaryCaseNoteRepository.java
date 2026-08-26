package com.university.lms.disciplinary.repository;

import com.university.lms.disciplinary.domain.DisciplinaryCaseNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisciplinaryCaseNoteRepository extends JpaRepository<DisciplinaryCaseNote, UUID> {

    List<DisciplinaryCaseNote> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}
