package com.university.lms.communication.repository;

import com.university.lms.communication.domain.ConversationParticipant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the communication module. */
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    List<ConversationParticipant> findByConversationId(UUID conversationId);

    List<ConversationParticipant> findByUserId(UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
}
