package com.university.lms.academic.web;

import com.university.lms.academic.dto.AcademicTermResponse;
import com.university.lms.academic.dto.AcademicYearResponse;
import com.university.lms.academic.dto.CreateAcademicYearRequest;
import com.university.lms.academic.service.AcademicCalendarService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Academic years, e.g. {@code 2026/2027}. */
@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearController {

    private final AcademicCalendarService service;

    public AcademicYearController(AcademicCalendarService service) {
        this.service = service;
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping
    public ResponseEntity<AcademicYearResponse> create(@Valid @RequestBody CreateAcademicYearRequest request) {
        AcademicYearResponse created = service.createYear(request);
        return ResponseEntity.created(URI.create("/api/v1/academic-years/" + created.id()))
                .body(created);
    }

    @AccessClass(PUBLIC)
    @GetMapping
    public PageResponse<AcademicYearResponse> findAll(
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findYears(pageable);
    }

    @AccessClass(PUBLIC)
    @GetMapping("/{id}")
    public AcademicYearResponse findById(@PathVariable UUID id) {
        return service.findYear(id);
    }

    /** Not paged: a year holds a handful of terms, bounded by the calendar. */
    @AccessClass(PUBLIC)
    @GetMapping("/{id}/terms")
    public List<AcademicTermResponse> terms(@PathVariable UUID id) {
        return service.findTermsOfYear(id);
    }
}
