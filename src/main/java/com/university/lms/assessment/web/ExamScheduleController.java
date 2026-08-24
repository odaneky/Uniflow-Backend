package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.CancelExamRequest;
import org.springframework.web.bind.annotation.PutMapping;
import com.university.lms.assessment.dto.ExamMisconductRecordResponse;
import com.university.lms.assessment.dto.ExamSittingResponse;
import com.university.lms.assessment.dto.ReportExamMisconductRequest;
import com.university.lms.assessment.dto.ScheduleExamRequest;
import com.university.lms.assessment.service.ExamScheduleService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exam scheduling, for the examinations office.
 *
 * <p>Lives under {@code /courses/sections} so it inherits the same authorisation as the rest of
 * timetabling — scheduling an exam is the same kind of act as scheduling a class, done by the same
 * people.
 *
 * <p>Scheduling and publishing are separate calls on purpose. A draft timetable is worked on for
 * weeks and is wrong for most of that time; students see nothing until it is released.
 */
@RestController
@RequestMapping("/api/v1/courses/sections")
public class ExamScheduleController {

    private final ExamScheduleService examScheduleService;

    public ExamScheduleController(ExamScheduleService examScheduleService) {
        this.examScheduleService = examScheduleService;
    }

    /**
     * The whole term's exam timetable, drafts included.
     *
     * <p>Staff only, and gated explicitly in {@code SecurityConfig} — it exposes unpublished
     * sittings, which are exactly what students must not see.
     */
    @AccessClass(STAFF_ONLY)
    @GetMapping("/exams")
    public List<ExamSittingResponse> forTerm(@RequestParam UUID academicTermId) {
        return examScheduleService.forTerm(academicTermId);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/{sectionId}/exams")
    public List<ExamSittingResponse> forSection(@PathVariable UUID sectionId) {
        return examScheduleService.forSection(sectionId);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/{sectionId}/exams")
    public ResponseEntity<ExamSittingResponse> schedule(
            @PathVariable UUID sectionId, @Valid @RequestBody ScheduleExamRequest request) {
        ExamSittingResponse created = examScheduleService.schedule(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/courses/sections/" + sectionId + "/exams"))
                .body(created);
    }

    /**
     * Moves a sitting. If it was published, everyone sitting it is notified.
     */
    @AccessClass(STAFF_ONLY)
    @PutMapping("/exams/{sittingId}")
    public ExamSittingResponse reschedule(
            @PathVariable UUID sittingId, @Valid @RequestBody ScheduleExamRequest request) {
        return examScheduleService.reschedule(sittingId, request);
    }

    /** Takes a published sitting back to draft, to correct it before republishing. */
    @AccessClass(STAFF_ONLY)
    @PostMapping("/exams/{sittingId}/unpublish")
    public ExamSittingResponse unpublish(@PathVariable UUID sittingId) {
        return examScheduleService.unpublish(sittingId);
    }

    /**
     * Cancels a sitting. The record remains — academic history stays auditable — but it leaves
     * student timetables and stops holding its hall.
     */
    @AccessClass(STAFF_ONLY)
    @PostMapping("/exams/{sittingId}/cancel")
    public ExamSittingResponse cancel(
            @PathVariable UUID sittingId, @Valid @RequestBody(required = false) CancelExamRequest request) {
        return examScheduleService.cancel(sittingId, request == null ? null : request.reason());
    }

    /** Releases the sitting to the students enrolled in the section. */
    @AccessClass(STAFF_ONLY)
    @PostMapping("/exams/{sittingId}/publish")
    public ExamSittingResponse publish(@PathVariable UUID sittingId) {
        return examScheduleService.publish(sittingId);
    }

    /** Files a conduct report against a candidate sitting this exam. */
    @AccessClass(STAFF_ONLY)
    @PostMapping("/exams/{sittingId}/misconduct")
    @ResponseStatus(HttpStatus.CREATED)
    public ExamMisconductRecordResponse reportMisconduct(
            @PathVariable UUID sittingId, @Valid @RequestBody ReportExamMisconductRequest request) {
        return examScheduleService.reportMisconduct(sittingId, request);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/exams/{sittingId}/misconduct")
    public List<ExamMisconductRecordResponse> misconduct(@PathVariable UUID sittingId) {
        return examScheduleService.misconductFor(sittingId);
    }
}
