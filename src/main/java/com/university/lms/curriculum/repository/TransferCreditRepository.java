package com.university.lms.curriculum.repository;

import com.university.lms.curriculum.domain.TransferCredit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferCreditRepository extends JpaRepository<TransferCredit, UUID> {

    List<TransferCredit> findByStudentIdOrderByAwardedAtDesc(UUID studentId);
}
