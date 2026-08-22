package com.university.lms.communication.repository;

import com.university.lms.communication.domain.Conversation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
