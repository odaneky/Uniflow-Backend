package com.university.lms.communication.repository;

import com.university.lms.communication.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the communication module. */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdAndDeletedAtIsNullOrderBySentAtDesc(UUID conversationId);

    Optional<Message> findByConversationIdAndIdempotencyKey(UUID conversationId, String idempotencyKey);

    @Query(
            """
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.senderUserId <> :userId
              and m.deletedAt is null
              and m.sentAt > coalesce(:lastReadAt, :epoch)
            """)
    long countUnreadInConversation(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("lastReadAt") Instant lastReadAt,
            @Param("epoch") Instant epoch);

    @Query(
            """
            select count(m) from Message m, ConversationParticipant p
            where p.conversation = m.conversation
              and p.userId = :userId
              and m.senderUserId <> :userId
              and m.deletedAt is null
              and m.sentAt > coalesce(p.lastReadAt, :epoch)
            """)
    long countTotalUnreadForUser(@Param("userId") UUID userId, @Param("epoch") Instant epoch);

    List<Message> findByConversationIdOrderBySentAtAscIdAsc(UUID conversationId);
}
