package com.university.lms.finance.repository;

import com.university.lms.finance.domain.StudentAccount;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAccountRepository extends JpaRepository<StudentAccount, UUID> {

    Optional<StudentAccount> findByStudentId(UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudentAccount a where a.studentId = :studentId")
    Optional<StudentAccount> lockByStudentId(@Param("studentId") UUID studentId);
}
