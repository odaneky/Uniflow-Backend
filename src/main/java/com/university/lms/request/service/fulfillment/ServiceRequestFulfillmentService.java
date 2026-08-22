package com.university.lms.request.service.fulfillment;

import com.university.lms.identity.api.CurrentUser;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ServiceRequestFulfillmentService {

    private final Map<ServiceRequestType, RequestFulfillmentApplier> appliers;

    public ServiceRequestFulfillmentService(List<RequestFulfillmentApplier> applierList) {
        this.appliers = applierList.stream()
                .collect(Collectors.toMap(RequestFulfillmentApplier::type, Function.identity(), (a, b) -> a));
    }

    public void fulfill(ServiceRequest request, CurrentUser actor) {
        RequestFulfillmentApplier applier = appliers.get(request.getRequestType());
        if (applier == null) {
            return;
        }
        applier.fulfill(request, actor);
    }
}
