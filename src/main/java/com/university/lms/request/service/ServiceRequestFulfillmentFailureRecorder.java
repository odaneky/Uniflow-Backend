package com.university.lms.request.service;

import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.repository.ServiceRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records why fulfilment failed, in a transaction of its own.
 *
 * <p>Split out of {@link ServiceRequestService#complete} for the same reason
 * {@code OutboxRowProcessor} is split out of {@code OutboxDispatcher}: {@code complete} is
 * {@code @Transactional}, and a fulfilment failure re-thrown out of it rolls that whole
 * transaction back — including the {@code markFulfillmentFailed} write meant to explain what
 * happened. The failure the caller sees was real; the record that it happened has to survive the
 * rollback the failure itself causes, which means writing it somewhere that rollback cannot reach.
 * {@code REQUIRES_NEW} only creates a genuinely independent transaction when the call arrives
 * through the Spring proxy, so this has to be a separate bean, not a private method.
 */
@Component
class ServiceRequestFulfillmentFailureRecorder {

    private final ServiceRequestRepository requestRepository;

    ServiceRequestFulfillmentFailureRecorder(ServiceRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(java.util.UUID requestId, String errorMessage) {
        ServiceRequest request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Service request " + requestId + " vanished mid-fulfilment"));
        request.markFulfillmentFailed(errorMessage);
        requestRepository.save(request);
    }
}
