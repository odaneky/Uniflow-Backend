package com.university.lms.communication.service;

import com.university.lms.communication.api.MessageDirectory;
import com.university.lms.communication.domain.Conversation;
import com.university.lms.communication.domain.Message;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.communication.repository.ConversationRepository;
import com.university.lms.communication.repository.MessageRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultMessageDirectory implements MessageDirectory {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;

    public DefaultMessageDirectory(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public Optional<MessageSummary> findById(UUID messageId) {
        return messageRepository.findById(messageId).map(this::toSummary);
    }

    @Override
    public List<UUID> participantUserIds(UUID conversationId) {
        return participantRepository.findByConversationId(conversationId).stream()
                .map(p -> p.getUserId())
                .toList();
    }

    private MessageSummary toSummary(Message message) {
        String preview = message.getBody();
        if (preview.length() > 120) {
            preview = preview.substring(0, 117) + "...";
        }
        String subject = conversationRepository
                .findById(message.getConversation().getId())
                .map(Conversation::getSubject)
                .orElse("Conversation");
        return new MessageSummary(
                message.getId(),
                message.getConversation().getId(),
                message.getSenderUserId(),
                preview,
                subject);
    }
}
