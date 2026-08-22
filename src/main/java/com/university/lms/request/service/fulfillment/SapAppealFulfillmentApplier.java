package com.university.lms.request.service.fulfillment;

import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.service.ServiceHoldService;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import org.springframework.stereotype.Component;

@Component
public class SapAppealFulfillmentApplier implements RequestFulfillmentApplier {

    private final ServiceHoldService serviceHoldService;

    public SapAppealFulfillmentApplier(ServiceHoldService serviceHoldService) {
        this.serviceHoldService = serviceHoldService;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.SAP_APPEAL;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        serviceHoldService.clearActiveHoldsOfType(request.getStudentId(), HoldType.SAP);
    }
}
