package com.university.lms.student.repository;

import com.university.lms.student.domain.AdvisingNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisingNoteRepository extends JpaRepository<AdvisingNote, UUID> {

    List<AdvisingNote> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
