package com.university.lms.communication.repository;

import com.university.lms.communication.domain.ForumPost;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {

    Page<ForumPost> findByTopicIdAndDeletedAtIsNullOrderBySentAtAscIdAsc(UUID topicId, Pageable pageable);

    long countByTopicIdAndDeletedAtIsNull(UUID topicId);

    @Query(
            """
            select count(p) from ForumPost p
            where p.topic.id = :topicId and p.deletedAt is null and p.id <> :rootPostId
            """)
    long countReplies(@Param("topicId") UUID topicId, @Param("rootPostId") UUID rootPostId);
}
