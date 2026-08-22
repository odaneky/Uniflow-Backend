package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.service.ServiceRequestPayloads;
import org.springframework.stereotype.Component;

/** Records an approved course substitution on the request for registrar follow-up in degree audit. */
@Component
public class CourseSubstitutionFulfillmentApplier implements RequestFulfillmentApplier {

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.COURSE_SUBSTITUTION;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        if (ServiceRequestPayloads.requiredCourseId(request.getPayload()) == null
                || ServiceRequestPayloads.substituteCourseId(request.getPayload()) == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "Missing substitution courses");
        }
    }
}
