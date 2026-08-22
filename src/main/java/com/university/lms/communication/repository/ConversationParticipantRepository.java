package com.university.lms.communication.repository;

import com.university.lms.communication.domain.ConversationParticipant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the communication module. */
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    java.util.List<ConversationParticipant> findByConversationId(UUID conversationId);

    java.util.List<ConversationParticipant> findByUserId(UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);
}
