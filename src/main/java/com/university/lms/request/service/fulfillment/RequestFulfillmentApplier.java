package com.university.lms.request.service.fulfillment;

import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;

/** Applies domain side effects when a request reaches COMPLETED. */
public interface RequestFulfillmentApplier {

    ServiceRequestType type();

    void fulfill(ServiceRequest request, CurrentUser actor);
}
