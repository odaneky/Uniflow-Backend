package com.university.lms.request.web;

import com.university.lms.request.dto.CreateServiceRequestRequest;
import com.university.lms.request.dto.ServiceRequestResponse;
import com.university.lms.request.dto.TransitionServiceRequestRequest;
import com.university.lms.request.service.ServiceRequestService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The caller's own registry requests. No student identifier is accepted. */
@RestController
@RequestMapping("/api/v1/me/requests")
public class MyRequestController {

    private final ServiceRequestService serviceRequestService;

    public MyRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping
    public List<ServiceRequestResponse> own() {
        return serviceRequestService.own();
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/{id}")
    public ServiceRequestResponse ownById(@PathVariable UUID id) {
        return serviceRequestService.ownById(id);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping
    public ResponseEntity<ServiceRequestResponse> create(@Valid @RequestBody CreateServiceRequestRequest request) {
        ServiceRequestResponse created = serviceRequestService.createOwn(request);
        return ResponseEntity.created(URI.create("/api/v1/me/requests/" + created.id())).body(created);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping("/{id}/cancel")
    public ServiceRequestResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TransitionServiceRequestRequest body) {
        return serviceRequestService.cancelOwn(id, body);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServiceRequestResponse attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return serviceRequestService.attachOwn(id, file);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/{id}/attachments/{documentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID id, @PathVariable UUID documentId) {
        return ServiceRequestController.fileResponse(serviceRequestService.downloadAttachmentOwn(id, documentId));
    }
}
