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

@ExtendWith(MockitoExtension.class)
class AcademicOrgUnitHandlerTest {

    @Mock
    private StaffingService staffingService;

    private AcademicOrgUnitHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new AcademicOrgUnitHandler(objectMapper, staffingService);
    }

    @Test
    void handlingTheEventEnsuresAnOrgUnitForTheSourceEntity() throws Exception {
        UUID facultyId = UUID.randomUUID();
        String payload = "{\"sourceType\":\"FACULTY\",\"sourceId\":\"" + facultyId + "\",\"code\":\"SCI\",\"name\":\"Science\"}";
        DomainOutbox row = new DomainOutbox("FACULTY", facultyId, "AcademicOrgUnitNeeded", payload, "test:" + UUID.randomUUID());

        handler.handle(row);

        verify(staffingService).ensureOrgUnitFor(eq("FACULTY"), eq(facultyId), eq("SCI"), eq("Science"));
    }
}
