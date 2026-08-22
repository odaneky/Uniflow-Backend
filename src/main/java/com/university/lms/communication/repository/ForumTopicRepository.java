package com.university.lms.communication.repository;

import com.university.lms.communication.domain.ForumTopic;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForumTopicRepository extends JpaRepository<ForumTopic, UUID> {

    Page<ForumTopic> findByCourseSectionIdOrderByPinnedDescUpdatedAtDesc(UUID courseSectionId, Pageable pageable);

    @Modifying
    @Query("update ForumTopic t set t.updatedAt = :at where t.id = :id")
    void touchUpdatedAt(@Param("id") UUID id, @Param("at") Instant at);
}
