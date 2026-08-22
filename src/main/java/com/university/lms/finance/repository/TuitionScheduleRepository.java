package com.university.lms.finance.repository;

import com.university.lms.finance.domain.TuitionSchedule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TuitionScheduleRepository extends JpaRepository<TuitionSchedule, UUID> {}
