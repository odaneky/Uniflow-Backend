package com.university.lms.request.service.fulfillment;

import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Routes an approved request to the applier that performs its domain effect.
 *
 * <p>Registration is validated eagerly, at construction, rather than left to be discovered the
 * first time a request of a given type is completed. Two failure modes used to be silent: a type
 * with no applier at all completed cleanly as a no-op — the approval succeeded, the audit trail
 * recorded a fulfilment, and nothing the request was actually for ever happened — and two appliers
 * registered for the same type silently kept the first and dropped the second. Both are now a
 * refusal to start, which is the correct place for a misconfiguration this consequential to fail.
 */
@Service
public class ServiceRequestFulfillmentService {

    private final Map<ServiceRequestType, RequestFulfillmentApplier> appliers;

    public ServiceRequestFulfillmentService(List<RequestFulfillmentApplier> applierList) {
        Map<ServiceRequestType, RequestFulfillmentApplier> registered = new EnumMap<>(ServiceRequestType.class);
        for (RequestFulfillmentApplier applier : applierList) {
            RequestFulfillmentApplier existing = registered.put(applier.type(), applier);
            if (existing != null) {
                throw new IllegalStateException("Two appliers registered for " + applier.type() + ": "
                        + existing.getClass().getName() + " and " + applier.getClass().getName());
            }
        }
        for (ServiceRequestType type : ServiceRequestType.values()) {
            if (!registered.containsKey(type)) {
                throw new IllegalStateException("No RequestFulfillmentApplier registered for " + type
                        + " — every request type must have exactly one, even if it does nothing but validate");
            }
        }
        this.appliers = registered;
    }

    public void fulfill(ServiceRequest request, CurrentUser actor) {
        RequestFulfillmentApplier applier = appliers.get(request.getRequestType());
        if (applier == null) {
            // Unreachable given the constructor's exhaustiveness check — kept as a refusal rather
            // than a silent no-op, in case a type is ever added to the enum without a matching bean.
            throw new IllegalStateException("No RequestFulfillmentApplier registered for " + request.getRequestType());
        }
        applier.fulfill(request, actor);
    }
}
