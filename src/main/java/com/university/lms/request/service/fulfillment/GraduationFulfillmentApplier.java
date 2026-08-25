package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import org.springframework.stereotype.Component;

@Component
public class GraduationFulfillmentApplier implements RequestFulfillmentApplier {

    private final DegreeAudit degreeAudit;

    public GraduationFulfillmentApplier(DegreeAudit degreeAudit) {
        this.degreeAudit = degreeAudit;
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
        // G3: recordConferral both writes the DegreeAward snapshot (GPA, credits, curriculum
        // version, honours) and drives the students.status flip — a bare studentLifecycle.graduate
        // used to do only the second half, leaving graduation with no conferral evidence at all.
        degreeAudit.recordConferral(request.getStudentId(), actor.userId());
    }
}
