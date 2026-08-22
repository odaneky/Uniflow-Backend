package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.grading.api.GradeAppealActions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.service.ServiceRequestPayloads;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AppealFulfillmentApplier implements RequestFulfillmentApplier {

    private final GradeAppealActions gradeAppealActions;

    public AppealFulfillmentApplier(GradeAppealActions gradeAppealActions) {
        this.gradeAppealActions = gradeAppealActions;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.APPEAL;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        UUID gradeId = ServiceRequestPayloads.gradeId(request.getPayload());
        if (gradeId == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "Missing grade on appeal");
        }
        gradeAppealActions.resolveAppeal(gradeId, actor.userId());
    }
}
