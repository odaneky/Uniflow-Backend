package com.university.lms.request.service.fulfillment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.student.api.StudentLifecycle;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * D5: a programme transfer previously had no request/review path at all — the only way to change
 * a student's programme was a registrar's direct, unreviewed {@code PATCH /students/{id}}.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammeTransferFulfillmentApplierTest {

    @Mock
    private StudentLifecycle studentLifecycle;

    @Test
    @DisplayName("fulfilling an approved transfer applies it")
    void fulfillAppliesTheTransfer() {
        UUID studentId = UUID.randomUUID();
        UUID newProgrammeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        ServiceRequest request = new ServiceRequest(
                studentId,
                ServiceRequestType.PROGRAMME_TRANSFER,
                "PT-000001",
                null,
                "{\"newProgrammeId\":\"" + newProgrammeId + "\",\"reason\":\"Changing majors\"}",
                null,
                Instant.now().plus(14, ChronoUnit.DAYS));
        CurrentUser actor = new CurrentUser(
                actorId, "subject", "registrar", "registrar@university.test", "Rita Registrar", Optional.empty(), Set.of(), Set.of());

        new ProgrammeTransferFulfillmentApplier(studentLifecycle).fulfill(request, actor);

        verify(studentLifecycle).transferProgramme(studentId, newProgrammeId, "Changing majors", actorId);
    }

    @Test
    @DisplayName("a payload missing the target programme is refused before anything is applied")
    void missingProgrammeIdIsRefused() {
        ServiceRequest request = new ServiceRequest(
                UUID.randomUUID(),
                ServiceRequestType.PROGRAMME_TRANSFER,
                "PT-000002",
                null,
                "{}",
                null,
                Instant.now().plus(14, ChronoUnit.DAYS));
        CurrentUser actor = new CurrentUser(
                UUID.randomUUID(), "subject", "registrar", "registrar@university.test", "Rita Registrar", Optional.empty(), Set.of(), Set.of());

        assertThatThrownBy(() -> new ProgrammeTransferFulfillmentApplier(studentLifecycle).fulfill(request, actor))
                .isInstanceOf(BusinessException.class);
    }
}
