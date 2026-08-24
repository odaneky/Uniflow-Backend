package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.curriculum.api.CourseSubstitutions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.service.ServiceRequestPayloads;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Records an approved course substitution — degree progress and the prerequisite check both
 * consult it (see {@code curriculum.api.CourseSubstitutions}) once this has run.
 */
@Component
public class CourseSubstitutionFulfillmentApplier implements RequestFulfillmentApplier {

    private final CourseSubstitutions courseSubstitutions;

    public CourseSubstitutionFulfillmentApplier(CourseSubstitutions courseSubstitutions) {
        this.courseSubstitutions = courseSubstitutions;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.COURSE_SUBSTITUTION;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        UUID requiredCourseId = ServiceRequestPayloads.requiredCourseId(request.getPayload());
        UUID substituteCourseId = ServiceRequestPayloads.substituteCourseId(request.getPayload());
        if (requiredCourseId == null || substituteCourseId == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "Missing substitution courses");
        }
        courseSubstitutions.record(
                request.getStudentId(), requiredCourseId, substituteCourseId, request.getId(), actor.userId());
    }
}
