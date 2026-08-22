package com.university.lms.request.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.request.dto.DecideServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.service.ServiceRequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registry queue for student service requests. */
@RestController
@RequestMapping("/api/v1/requests")
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;

    public ServiceRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    @GetMapping
    public PageResponse<ServiceRequestResponse> all(
            @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return serviceRequestService.all(pageable);
    }

    @PostMapping("/{id}/decide")
    public ServiceRequestResponse decide(
            @PathVariable UUID id, @Valid @RequestBody DecideServiceRequestRequest request) {
        return serviceRequestService.decide(id, request);
    }
}
