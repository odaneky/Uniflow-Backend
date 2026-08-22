package com.university.lms.communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.university.lms.common.outbox.DomainOutboxRepository;
import com.university.lms.common.outbox.OutboxDispatcher;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.communication.domain.Conversation;
import com.university.lms.communication.domain.ConversationParticipant;
import com.university.lms.communication.domain.Message;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.communication.repository.ConversationRepository;
import com.university.lms.communication.repository.MessageRepository;
import com.university.lms.notification.dispatch.MessageSentOutboxHandler;
import com.university.lms.notification.domain.NotificationType;
import com.university.lms.notification.repository.NotificationRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the durable outbox path from message event to notification row. */
class CommsOutboxIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private DomainOutboxRepository outboxRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AcademicFixtures fixtures;

    @Test
    @DisplayName("MessageSent outbox row becomes an IN_APP notification for other participants")
    void messageSentOutboxCreatesNotification() throws Exception {
        var sender = fixtures.user();
        var recipient = fixtures.user();

        Conversation conversation = conversationRepository.save(new Conversation("Integration thread", sender.getId()));
        participantRepository.save(new ConversationParticipant(conversation, sender.getId()));
        participantRepository.save(new ConversationParticipant(conversation, recipient.getId()));
        Message message = messageRepository.save(new Message(conversation, sender.getId(), "Hello from integration test"));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", message.getId().toString());
        payload.put("conversationId", conversation.getId().toString());
        payload.put("senderUserId", sender.getId().toString());
        payload.put("senderName", sender.fullName());

        outboxWriter.enqueue(
                "Message",
                message.getId(),
                MessageSentOutboxHandler.EVENT_TYPE,
                payload.toString(),
                "MessageSent:test:" + message.getId());

        assertThat(outboxRepository.count()).isPositive();

        long before = notificationRepository.count();
        outboxDispatcher.drainOnce();
        long after = notificationRepository.count();

        assertThat(after).isGreaterThan(before);
        assertThat(notificationRepository.findAll().stream()
                        .anyMatch(n -> n.getRecipientUserId().equals(recipient.getId())
                                && n.getNotificationType() == NotificationType.MESSAGE))
                .isTrue();
    }
}
