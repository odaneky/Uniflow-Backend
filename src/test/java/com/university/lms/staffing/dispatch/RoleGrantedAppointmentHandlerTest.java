package com.university.lms.staffing.dispatch;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.outbox.DomainOutbox;
import com.university.lms.staffing.service.StaffingService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A5 groundwork: proves the identity -&gt; staffing wiring end to end at the handler boundary — a
 * role-granted event results in exactly the appointment {@code StaffingService.ensureAppointment}
 * itself is separately tested to produce.
 */
@ExtendWith(MockitoExtension.class)
class RoleGrantedAppointmentHandlerTest {

    @Mock
    private StaffingService staffingService;

    private RoleGrantedAppointmentHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new RoleGrantedAppointmentHandler(objectMapper, staffingService);
    }

    @Test
    void handlingTheEventEnsuresAnAppointmentForTheGrantedRole() throws Exception {
        UUID userId = UUID.randomUUID();
        String payload = "{\"userId\":\"" + userId + "\",\"role\":\"LECTURER\"}";
        DomainOutbox row = new DomainOutbox("USER", userId, "IdentityRoleGranted", payload, "test:" + UUID.randomUUID());

        handler.handle(row);

        verify(staffingService).ensureAppointment(eq(userId), eq("LECTURER"));
    }
}
