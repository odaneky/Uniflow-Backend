package com.university.lms.request.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.request.domain.ServiceRequestStatus;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.dto.CompleteServiceRequestRequest;
import com.university.lms.request.dto.DecideServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.dto.TransitionServiceRequestRequest;
import com.university.lms.request.service.ServiceRequestService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    @AccessClass(STAFF_ONLY)
    public PageResponse<ServiceRequestResponse> search(
            @RequestParam(required = false) List<ServiceRequestStatus> status,
            @RequestParam(required = false) ServiceRequestType type,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String reference,
            @PageableDefault(size = 50, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return serviceRequestService.search(status, type, mine, reference, pageable);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/{id}")
    public ServiceRequestResponse byId(@PathVariable UUID id) {
        return serviceRequestService.staffById(id);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/{id}/claim")
    public ServiceRequestResponse claim(@PathVariable UUID id) {
        return serviceRequestService.claim(id);
    }

    @PostMapping("/{id}/review")
    @AccessClass(STAFF_ONLY)
    public ServiceRequestResponse startReview(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionServiceRequestRequest body) {
        return serviceRequestService.startReview(id, body);
    }

    @PostMapping("/{id}/approve")
    @AccessClass(STAFF_ONLY)
    public ServiceRequestResponse approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionServiceRequestRequest body) {
        return serviceRequestService.approve(id, body);
    }

    @PostMapping("/{id}/deny")
    @AccessClass(STAFF_ONLY)
    public ServiceRequestResponse deny(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionServiceRequestRequest body) {
        return serviceRequestService.deny(id, body);
    }

    @PostMapping("/{id}/complete")
    @AccessClass(STAFF_ONLY)
    public ServiceRequestResponse complete(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CompleteServiceRequestRequest body) {
        return serviceRequestService.complete(id, body);
    }

    /** @deprecated prefer explicit transition endpoints */
    @PostMapping("/{id}/decide")
    @AccessClass(STAFF_ONLY)
    public ServiceRequestResponse decide(
            @PathVariable UUID id, @Valid @RequestBody DecideServiceRequestRequest request) {
        return serviceRequestService.decide(id, request);
    }
}
