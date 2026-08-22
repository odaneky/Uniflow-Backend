package com.university.lms.curriculum.repository;

import com.university.lms.curriculum.domain.GraduationClearanceItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationClearanceItemRepository extends JpaRepository<GraduationClearanceItem, UUID> {

    List<GraduationClearanceItem> findByStudentIdOrderByItemTypeAsc(UUID studentId);
}
