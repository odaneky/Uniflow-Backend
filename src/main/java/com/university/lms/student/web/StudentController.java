package com.university.lms.student.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.student.domain.StudentStatus;
import com.university.lms.student.dto.AdvisorCandidateResponse;
import com.university.lms.student.dto.CreateStudentRequest;
import com.university.lms.student.dto.ProvisionStudentRequest;
import com.university.lms.student.dto.StudentResponse;
import com.university.lms.student.dto.StudentSummaryResponse;
import com.university.lms.student.dto.UpdateStudentRequest;
import com.university.lms.student.service.StudentProvisioningService;
import com.university.lms.student.service.StudentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student registry endpoints.
 *
 * <p>The controller does three things and nothing else: bind and validate input, delegate to the
 * application service, and map the result onto HTTP. No business rules, no repository access, and
 * no transaction boundary live here.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    private final StudentProvisioningService studentProvisioningService;

    public StudentController(StudentService studentService, StudentProvisioningService studentProvisioningService) {
        this.studentService = studentService;
            this.studentProvisioningService = studentProvisioningService;
    }

    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        StudentResponse created = studentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + created.id())).body(created);
    }

    /** Always paged — the collection grows to tens of thousands of rows. */
    @GetMapping
    public PageResponse<StudentSummaryResponse> search(
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) UUID programmeId,
            @RequestParam(required = false) UUID advisorUserId,
            @PageableDefault(size = 20, sort = "studentNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return studentService.search(status, programmeId, advisorUserId, pageable);
    }

    /**
     * The caller's own record.
     *
     * <p>Declared as a literal path alongside {@code /{id}}; Spring matches literals first, so
     * "me" is never parsed as a UUID.
     */
    /**
     * Academic provisioning, keyed by the institutional student number.
     *
     * <p>Prefer this over {@code POST /api/v1/students}: it does not require the student to have
     * signed in first, and it verifies the number against the identity provider rather than
     * trusting the caller that such a student exists.
     */
    @PostMapping("/provision")
    public ResponseEntity<StudentResponse> provision(@Valid @RequestBody ProvisionStudentRequest request) {
        StudentResponse created = studentProvisioningService.provision(request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + created.id())).body(created);
    }

    @GetMapping("/me")
    public StudentResponse findOwn() {
        return studentService.findOwn();
    }

    /** Staff picker for academic advisor assignment. Literal path before {@code /{id}}. */
    @GetMapping("/advisor-candidates")
    public List<AdvisorCandidateResponse> advisorCandidates() {
        return studentService.listAdvisorCandidates();
    }

    @GetMapping("/{id}")
    public StudentResponse findById(@PathVariable UUID id) {
        return studentService.findById(id);
    }

    @GetMapping("/by-number/{studentNumber}")
    public StudentResponse findByStudentNumber(@PathVariable String studentNumber) {
        return studentService.findByStudentNumber(studentNumber);
    }

    /** PATCH rather than PUT: absent fields mean "unchanged", not "clear". */
    @PatchMapping("/{id}")
    public StudentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.update(id, request);
    }
}
