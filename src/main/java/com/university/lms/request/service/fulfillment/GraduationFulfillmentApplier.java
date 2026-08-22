package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.student.api.StudentLifecycle;
import org.springframework.stereotype.Component;

@Component
public class GraduationFulfillmentApplier implements RequestFulfillmentApplier {

    private final DegreeAudit degreeAudit;
    private final StudentLifecycle studentLifecycle;

    public GraduationFulfillmentApplier(DegreeAudit degreeAudit, StudentLifecycle studentLifecycle) {
        this.degreeAudit = degreeAudit;
        this.studentLifecycle = studentLifecycle;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.GRADUATION;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        DegreeAudit.Eligibility eligibility = degreeAudit.eligibility(request.getStudentId());
        if (!eligibility.eligible()) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_FULFILLMENT_FAILED,
                    "Student is not eligible to graduate: " + String.join("; ", eligibility.blockers()));
        }
        studentLifecycle.graduate(request.getStudentId(), actor.userId());
    }
}
