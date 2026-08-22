package com.university.lms.enrollment.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.dto.CheckoutEnrollmentsRequest;
import com.university.lms.enrollment.dto.CheckoutEnrollmentsResponse;
import com.university.lms.enrollment.dto.CreateEnrollmentRequest;
import com.university.lms.enrollment.dto.EnrollmentOverrideRequest;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.university.lms.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration endpoints.
 *
 * <p>Lifecycle changes are modelled as named sub-resource actions ({@code /drop},
 * {@code /withdraw}) rather than a {@code PATCH} that accepts an arbitrary target status. Dropping
 * and withdrawing differ in their academic consequences, and an endpoint that took a status field
 * would invite a client to attempt transitions the domain forbids.
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /** 201 on success; waitlisted when the occurrence is full. */
    @PostMapping
    public ResponseEntity<EnrollmentResponse> enrol(@Valid @RequestBody CreateEnrollmentRequest request) {
        EnrollmentResponse created = enrollmentService.enrol(request);
        return ResponseEntity.created(URI.create("/api/v1/enrollments/" + created.id())).body(created);
    }

    /** Confirms a registration cart: min/max load, clashes, then enrol or waitlist each occurrence. */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutEnrollmentsResponse> checkout(
            @Valid @RequestBody CheckoutEnrollmentsRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CheckoutEnrollmentsResponse created = enrollmentService.checkout(request, idempotencyKey);
        return ResponseEntity.status(201).body(created);
    }

    @PostMapping("/override")
    public ResponseEntity<EnrollmentResponse> override(@Valid @RequestBody EnrollmentOverrideRequest request) {
        EnrollmentResponse created = enrollmentService.overrideEnrol(request);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    public PageResponse<EnrollmentResponse> search(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID courseSectionId,
            @RequestParam(required = false) EnrollmentStatus status,
            @PageableDefault(size = 20, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return enrollmentService.search(studentId, courseSectionId, status, pageable);
    }

    @GetMapping("/{id}")
    public EnrollmentResponse findById(@PathVariable UUID id) {
        return enrollmentService.findById(id);
    }

    @PostMapping("/{id}/drop")
    public EnrollmentResponse drop(@PathVariable UUID id) {
        return enrollmentService.drop(id);
    }

    @PostMapping("/{id}/withdraw")
    public EnrollmentResponse withdraw(@PathVariable UUID id) {
        return enrollmentService.withdraw(id);
    }

    @PostMapping("/{id}/complete")
    public EnrollmentResponse complete(@PathVariable UUID id) {
        return enrollmentService.complete(id);
    }

    @PostMapping("/{id}/approve")
    public EnrollmentResponse approve(@PathVariable UUID id) {
        return enrollmentService.approvePending(id);
    }

    @PostMapping("/{id}/accept-waitlist-offer")
    public EnrollmentResponse acceptWaitlistOffer(@PathVariable UUID id) {
        return enrollmentService.acceptWaitlistOffer(id);
    }

    @PostMapping("/{id}/decline-waitlist-offer")
    public EnrollmentResponse declineWaitlistOffer(@PathVariable UUID id) {
        return enrollmentService.declineWaitlistOffer(id);
    }
}
