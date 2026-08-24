package com.university.lms.request.service.fulfillment;

import com.university.lms.common.exception.BusinessException;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.RequestErrorCode;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.service.ServiceRequestPayloads;
import com.university.lms.student.api.StudentLifecycle;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies an approved programme transfer — the request/review path {@code docs} calls for
 * alongside the registrar's existing direct {@code PATCH /students/{id}}, which is left in place
 * as an administrative override rather than removed.
 */
@Component
public class ProgrammeTransferFulfillmentApplier implements RequestFulfillmentApplier {

    private final StudentLifecycle studentLifecycle;

    public ProgrammeTransferFulfillmentApplier(StudentLifecycle studentLifecycle) {
        this.studentLifecycle = studentLifecycle;
    }

    @Override
    public ServiceRequestType type() {
        return ServiceRequestType.PROGRAMME_TRANSFER;
    }

    @Override
    public void fulfill(ServiceRequest request, CurrentUser actor) {
        UUID newProgrammeId = ServiceRequestPayloads.newProgrammeId(request.getPayload());
        if (newProgrammeId == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_PAYLOAD, "Missing newProgrammeId");
        }
        String reason = ServiceRequestPayloads.reason(request.getPayload());
        studentLifecycle.transferProgramme(request.getStudentId(), newProgrammeId, reason, actor.userId());
    }
}
