package com.university.lms.communication.repository;

import com.university.lms.communication.domain.Conversation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the communication module. */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query(
            """
            select c from Conversation c
            where exists (
                select 1 from ConversationParticipant p
                where p.conversation = c and p.userId = :userId
            )
            """)
    Page<Conversation> findAllForParticipant(UUID userId, Pageable pageable);

    @Query(
            """
            select c from Conversation c
            where ((:sectionId is null and c.courseSectionId is null) or c.courseSectionId = :sectionId)
              and (select count(p) from ConversationParticipant p where p.conversation = c) = :size
              and (select count(p) from ConversationParticipant p where p.conversation = c and p.userId in :userIds) = :size
            """)
    List<Conversation> findByExactParticipants(
            @Param("sectionId") UUID sectionId, @Param("userIds") Set<UUID> userIds, @Param("size") int size);
}
