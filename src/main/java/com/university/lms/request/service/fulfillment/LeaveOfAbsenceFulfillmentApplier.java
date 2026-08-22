package com.university.lms.request.service.fulfillment;

import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.student.api.StudentLifecycle;
import org.springframework.stereotype.Component;

@Component
public class LeaveOfAbsenceFulfillmentApplier implements RequestFulfillmentApplier {

    private final StudentLifecycle studentLifecycle;

    public LeaveOfAbsenceFulfillmentApplier(StudentLifecycle studentLifecycle) {
        this.studentLifecycle = studentLifecycle;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.LEAVE_OF_ABSENCE;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        studentLifecycle.beginLeave(request.getStudentId(), actor.userId());
    }
}
