package com.university.lms.admissions.repository;

import com.university.lms.admissions.domain.ApplicationScore;
import com.university.lms.admissions.domain.ApplicationScore.ApplicationScoreId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationScoreRepository extends JpaRepository<ApplicationScore, ApplicationScoreId> {

    List<ApplicationScore> findByApplicationIdOrderByScoredAtAsc(UUID applicationId);
}
