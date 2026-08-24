package com.university.lms.identity.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.outbox.OutboxWriter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityOutboxPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    private IdentityOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new IdentityOutboxPublisher(outboxWriter, new ObjectMapper());
    }

    @Test
    void publishRoleGrantedEnqueuesTheEventWithTheUserAndRole() {
        UUID userId = UUID.randomUUID();
        when(outboxWriter.enqueue(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new DomainOutbox("USER", userId, "IdentityRoleGranted", "{}", "k"));

        publisher.publishRoleGranted(userId, "LECTURER");

        verify(outboxWriter)
                .enqueue(
                        eq("USER"),
                        eq(userId),
                        eq(IdentityOutboxPublisher.EVENT_ROLE_GRANTED),
                        contains("\"role\":\"LECTURER\""),
                        anyString());
    }

    @Test
    void aFailureToEnqueueDoesNotPropagate() {
        when(outboxWriter.enqueue(anyString(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        publisher.publishRoleGranted(UUID.randomUUID(), "LECTURER");
    }
}
