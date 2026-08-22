package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.enrollment.api.EnrollmentActions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.service.ServiceRequestPayloads;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalFulfillmentApplier implements RequestFulfillmentApplier {

    private final EnrollmentActions enrollmentActions;

    public WithdrawalFulfillmentApplier(EnrollmentActions enrollmentActions) {
        this.enrollmentActions = enrollmentActions;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.WITHDRAWAL;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        UUID enrollmentId = ServiceRequestPayloads.enrollmentId(request.getPayload());
        if (enrollmentId == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "Missing enrolment on request");
        }
        enrollmentActions.withdraw(enrollmentId, actor.userId());
    }
}
