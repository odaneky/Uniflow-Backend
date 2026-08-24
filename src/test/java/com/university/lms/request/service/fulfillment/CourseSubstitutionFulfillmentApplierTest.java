package com.university.lms.request.service.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.curriculum.api.CourseSubstitutions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Before this applier actually recorded anything, a {@code COURSE_SUBSTITUTION} request completed
 * successfully — validating that its payload named two courses — and left no trace anywhere that
 * the substitution had been approved.
 */
@ExtendWith(MockitoExtension.class)
class CourseSubstitutionFulfillmentApplierTest {

    @Mock
    private CourseSubstitutions courseSubstitutions;

    @Test
    @DisplayName("fulfilling an approved substitution records it")
    void fulfillRecordsTheSubstitution() {
        UUID studentId = UUID.randomUUID();
        UUID requiredCourseId = UUID.randomUUID();
        UUID substituteCourseId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        ServiceRequest request = new ServiceRequest(
                studentId,
                ServiceRequestType.COURSE_SUBSTITUTION,
                "CS-000001",
                null,
                "{\"requiredCourseId\":\"" + requiredCourseId + "\",\"substituteCourseId\":\"" + substituteCourseId
                        + "\"}",
                null);
        CurrentUser actor = new CurrentUser(
                actorId, "subject", "registrar", "registrar@university.test", "Rita Registrar", Optional.empty(), Set.of(), Set.of());

        new CourseSubstitutionFulfillmentApplier(courseSubstitutions).fulfill(request, actor);

        verify(courseSubstitutions).record(studentId, requiredCourseId, substituteCourseId, request.getId(), actorId);
    }

    @Test
    @DisplayName("a payload missing either course is refused before anything is recorded")
    void missingPayloadIsRefused() {
        ServiceRequest request = new ServiceRequest(
                UUID.randomUUID(), ServiceRequestType.COURSE_SUBSTITUTION, "CS-000002", null, "{}", null);
        CurrentUser actor = new CurrentUser(
                UUID.randomUUID(), "subject", "registrar", "registrar@university.test", "Rita Registrar", Optional.empty(), Set.of(), Set.of());

        assertThatThrownBy(() -> new CourseSubstitutionFulfillmentApplier(courseSubstitutions).fulfill(request, actor))
                .isInstanceOf(BusinessException.class);
    }
}
