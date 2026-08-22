package com.university.lms.request.web;

import com.university.lms.request.dto.CreateServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.dto.TransitionServiceRequestRequest;
import com.university.lms.request.service.ServiceRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own registry requests. No student identifier is accepted. */
@RestController
@RequestMapping("/api/v1/me/requests")
public class MyRequestController {

    private final ServiceRequestService serviceRequestService;

    public MyRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    @GetMapping
    public List<ServiceRequestResponse> own() {
        return serviceRequestService.own();
    }

    @GetMapping("/{id}")
    public ServiceRequestResponse ownById(@PathVariable UUID id) {
        return serviceRequestService.ownById(id);
    }

    @PostMapping
    public ResponseEntity<ServiceRequestResponse> create(@Valid @RequestBody CreateServiceRequestRequest request) {
        ServiceRequestResponse created = serviceRequestService.createOwn(request);
        return ResponseEntity.created(URI.create("/api/v1/me/requests/" + created.id())).body(created);
    }

    @PostMapping("/{id}/cancel")
    public ServiceRequestResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionServiceRequestRequest body) {
        return serviceRequestService.cancelOwn(id, body);
    }
}
