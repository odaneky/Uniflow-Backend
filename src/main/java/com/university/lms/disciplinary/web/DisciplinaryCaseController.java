package com.university.lms.disciplinary.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.disciplinary.dto.AssignCaseOfficerRequest;
import com.university.lms.disciplinary.dto.CreateDisciplinaryCaseNoteRequest;
import com.university.lms.disciplinary.dto.CreateDisciplinaryCaseRequest;
import com.university.lms.disciplinary.dto.DisciplinaryCaseNoteResponse;
import com.university.lms.disciplinary.dto.DisciplinaryCaseResponse;
import com.university.lms.disciplinary.dto.ResolveDisciplinaryCaseRequest;
import com.university.lms.disciplinary.service.DisciplinaryCaseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code @AccessClass(STAFF_ONLY)} throughout is the coarse layer only — every method additionally
 * narrows to the registry or this specific case's filer/assigned officer inside {@code
 * DisciplinaryCaseService}, since which staff member that is varies case by case and cannot be
 * expressed as a role in {@code SecurityConfig}'s URL matcher.
 */
@RestController
@RequestMapping("/api/v1")
public class DisciplinaryCaseController {

    private final DisciplinaryCaseService service;

    public DisciplinaryCaseController(DisciplinaryCaseService service) {
        this.service = service;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/disciplinary-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public DisciplinaryCaseResponse file(@Valid @RequestBody CreateDisciplinaryCaseRequest request) {
        return service.fileCase(request);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/disciplinary-cases/{id}")
    public DisciplinaryCaseResponse find(@PathVariable UUID id) {
        return service.find(id);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/disciplinary-cases/{id}/assign")
    public DisciplinaryCaseResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignCaseOfficerRequest request) {
        return service.assignOfficer(id, request);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/disciplinary-cases/{id}/close")
    public DisciplinaryCaseResponse close(@PathVariable UUID id, @Valid @RequestBody ResolveDisciplinaryCaseRequest request) {
        return service.close(id, request);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/disciplinary-cases/{id}/notes")
    public List<DisciplinaryCaseNoteResponse> notes(@PathVariable UUID id) {
        return service.listNotes(id);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/disciplinary-cases/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public DisciplinaryCaseNoteResponse addNote(@PathVariable UUID id, @Valid @RequestBody CreateDisciplinaryCaseNoteRequest request) {
        return service.addNote(id, request);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/students/{studentId}/disciplinary-cases")
    public List<DisciplinaryCaseResponse> forStudent(@PathVariable UUID studentId) {
        return service.listForStudent(studentId);
    }
}
