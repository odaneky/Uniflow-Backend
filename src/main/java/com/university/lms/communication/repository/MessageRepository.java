package com.university.lms.communication.repository;

import com.university.lms.communication.domain.Message;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the communication module. */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Always paged — a long-running thread is unbounded. */
    Page<Message> findByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    java.util.Optional<Message> findFirstByConversationIdOrderBySentAtDesc(UUID conversationId);
}
