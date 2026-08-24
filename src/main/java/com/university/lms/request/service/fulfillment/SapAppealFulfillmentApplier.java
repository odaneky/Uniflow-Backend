package com.university.lms.request.service.fulfillment;

import com.university.lms.financialaid.api.HoldActions;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import org.springframework.stereotype.Component;

@Component
public class SapAppealFulfillmentApplier implements RequestFulfillmentApplier {

    private final HoldActions holdActions;

    public SapAppealFulfillmentApplier(HoldActions holdActions) {
        this.holdActions = holdActions;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.SAP_APPEAL;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        holdActions.clearSapHold(request.getStudentId());
    }
}
