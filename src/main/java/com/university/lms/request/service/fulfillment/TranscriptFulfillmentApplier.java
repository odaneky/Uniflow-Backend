package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import org.springframework.stereotype.Component;

@Component
public class TranscriptFulfillmentApplier implements RequestFulfillmentApplier {

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.TRANSCRIPT;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        DeliverableFulfillment.requireDeliverable(request);
    }
}

@Component
class VerificationFulfillmentApplier implements RequestFulfillmentApplier {

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.VERIFICATION;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        DeliverableFulfillment.requireDeliverable(request);
    }
}

final class DeliverableFulfillment {
    private DeliverableFulfillment() {}

    static void requireDeliverable(ServiceRequest request) {
        if (request.getDeliverableDocumentId() == null) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_DELIVERABLE_REQUIRED,
                    "A deliverable document must be attached before completing this request");
        }
    }
}
