package com.university.lms.finance.repository;

import com.university.lms.finance.domain.TuitionSchedule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TuitionScheduleRepository extends JpaRepository<TuitionSchedule, UUID> {

    /** The currently-effective schedule — at most one row is ever open. */
    Optional<TuitionSchedule> findByEffectiveToIsNull();
}
