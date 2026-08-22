package com.university.lms.financialaid.repository;

import com.university.lms.financialaid.domain.ServiceHold;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceHoldRepository extends JpaRepository<ServiceHold, UUID> {

    List<ServiceHold> findByStudentIdAndActiveTrueOrderByPlacedAtDesc(UUID studentId);

    List<ServiceHold> findByStudentIdOrderByPlacedAtDesc(UUID studentId);
}
