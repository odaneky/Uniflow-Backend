package com.university.lms.notification.dispatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.notification.domain.Notification;
import com.university.lms.notification.service.NotificationDeliveryService;
import com.university.lms.request.api.RequestDirectory;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The submitted-request notification used to reach only {@code assignedTo}, which is null for
 * every request type except WITHDRAWAL. Ten of eleven types therefore notified no one on staff.
 * These tests pin the fix: every REGISTRAR is notified regardless of type, and a specific
 * assignee — when there is one — is notified too, without a duplicate.
 */
@ExtendWith(MockitoExtension.class)
class ServiceRequestOutboxHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Mock
    private RequestDirectory requestDirectory;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Mock
    private UserDirectory userDirectory;

    private ServiceRequestOutboxHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ServiceRequestOutboxHandler(objectMapper, requestDirectory, notificationDeliveryService, userDirectory);
    }

    private DomainOutbox rowFor(ServiceRequestType type) {
        String payload = "{\"requestId\":\"" + REQUEST_ID + "\",\"reference\":\"TR-0001\",\"requestType\":\"" + type + "\"}";
        return new DomainOutbox("ServiceRequest", REQUEST_ID, "ServiceRequestSubmitted", payload, "test:" + UUID.randomUUID());
    }

    @Test
    void everyRegistrarIsNotifiedEvenWithNoSpecificAssignee() throws Exception {
        UUID registrarA = UUID.randomUUID();
        UUID registrarB = UUID.randomUUID();
        when(requestDirectory.findById(REQUEST_ID))
                .thenReturn(Optional.of(new RequestDirectory.RequestSummary(
                        REQUEST_ID, UUID.randomUUID(), UUID.randomUUID(), ServiceRequestType.TRANSCRIPT,
                        ServiceRequestStatus.SUBMITTED, "TR-0001", null, null, Instant.now())));
        when(userDirectory.findByRealmRole(SecurityRoles.REGISTRAR))
                .thenReturn(List.of(
                        new UserDirectory.UserSummary(registrarA, "rega", "Reg A", "a@test", true),
                        new UserDirectory.UserSummary(registrarB, "regb", "Reg B", "b@test", true)));

        handler.handle(rowFor(ServiceRequestType.TRANSCRIPT));

        verify(notificationDeliveryService, times(2)).deliverInApp(any(Notification.class));
    }

    @Test
    void aSpecificAssigneeIsNotifiedInAdditionToRegistrarsWithoutDuplication() throws Exception {
        UUID advisor = UUID.randomUUID();
        when(requestDirectory.findById(REQUEST_ID))
                .thenReturn(Optional.of(new RequestDirectory.RequestSummary(
                        REQUEST_ID, UUID.randomUUID(), UUID.randomUUID(), ServiceRequestType.WITHDRAWAL,
                        ServiceRequestStatus.SUBMITTED, "WD-0001", advisor, null, Instant.now())));
        when(userDirectory.findByRealmRole(SecurityRoles.REGISTRAR))
                .thenReturn(List.of(new UserDirectory.UserSummary(advisor, "adv", "Advisor", "adv@test", true)));

        handler.handle(rowFor(ServiceRequestType.WITHDRAWAL));

        // Same person as both the assignee and (hypothetically) a registrar: exactly one notification.
        verify(notificationDeliveryService, times(1)).deliverInApp(any(Notification.class));
    }

    @Test
    void noRegistrarsAndNoAssigneeNotifiesNoOne() throws Exception {
        when(requestDirectory.findById(REQUEST_ID))
                .thenReturn(Optional.of(new RequestDirectory.RequestSummary(
                        REQUEST_ID, UUID.randomUUID(), UUID.randomUUID(), ServiceRequestType.SAP_APPEAL,
                        ServiceRequestStatus.SUBMITTED, "SA-0001", null, null, Instant.now())));
        when(userDirectory.findByRealmRole(SecurityRoles.REGISTRAR)).thenReturn(List.of());

        handler.handle(rowFor(ServiceRequestType.SAP_APPEAL));

        verify(notificationDeliveryService, never()).deliverInApp(any(Notification.class));
    }
}
