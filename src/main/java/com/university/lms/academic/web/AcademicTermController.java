package com.university.lms.academic.web;

import com.university.lms.academic.dto.AcademicTermResponse;
import com.university.lms.academic.dto.ExamWindowRequest;
import com.university.lms.academic.dto.AddDropWindowRequest;
import com.university.lms.academic.dto.CreateAcademicTermRequest;
import com.university.lms.academic.dto.RegistrationWindowRequest;
import com.university.lms.academic.service.AcademicCalendarService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Academic terms, and the registration window that decides whether enrolment is currently possible.
 *
 * <p>A term with no window is closed. Enrolment therefore has to be opened deliberately rather than
 * being available by default because a field was never set.
 */
@RestController
@RequestMapping("/api/v1/academic-terms")
public class AcademicTermController {

    private final AcademicCalendarService service;

    public AcademicTermController(AcademicCalendarService service) {
        this.service = service;
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping
    public ResponseEntity<AcademicTermResponse> create(@Valid @RequestBody CreateAcademicTermRequest request) {
        AcademicTermResponse created = service.createTerm(request);
        return ResponseEntity.created(URI.create("/api/v1/academic-terms/" + created.id()))
                .body(created);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/{id}")
    public AcademicTermResponse findById(@PathVariable UUID id) {
        return service.findTerm(id);
    }

    /**
     * Sets the registration window. PUT because it replaces the window wholesale and repeating the
     * call with the same body leaves the term in the same state.
     */
    @AccessClass(REGISTRY_ONLY)
    @PutMapping("/{id}/exam-window")
    public AcademicTermResponse setExamWindow(
            @PathVariable UUID id, @Valid @RequestBody ExamWindowRequest request) {
        return service.setExamWindow(id, request);
    }

    @AccessClass(REGISTRY_ONLY)
    @PutMapping("/{id}/registration-window")
    public AcademicTermResponse setRegistrationWindow(
            @PathVariable UUID id, @Valid @RequestBody RegistrationWindowRequest request) {
        return service.setRegistrationWindow(id, request);
    }

    @AccessClass(REGISTRY_ONLY)
    @PutMapping("/{id}/add-drop-window")
    public AcademicTermResponse setAddDropWindow(
            @PathVariable UUID id, @Valid @RequestBody AddDropWindowRequest request) {
        return service.setAddDropWindow(id, request);
    }
}
