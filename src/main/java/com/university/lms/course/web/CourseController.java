package com.university.lms.course.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.course.domain.CourseStatus;
import com.university.lms.course.dto.AssignedLecturerResponse;
import com.university.lms.course.dto.ScheduleCheckRequest;
import com.university.lms.course.dto.ScheduleCheckResponse;
import com.university.lms.course.dto.AssignLecturerRequest;
import com.university.lms.course.dto.CourseResponse;
import com.university.lms.course.dto.CourseSectionResponse;
import com.university.lms.course.dto.CourseSummaryResponse;
import com.university.lms.course.dto.CreateCourseRequest;
import com.university.lms.course.dto.CreateCourseSectionRequest;
import com.university.lms.course.dto.ReplaceCourseRequirementsRequest;
import com.university.lms.course.dto.ReplaceSectionMeetingsRequest;
import com.university.lms.course.dto.UpdateCourseRequest;
import com.university.lms.course.dto.UpdateSectionRequest;
import com.university.lms.course.service.CourseService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Course catalog endpoints, plus the sections belonging to a course. */
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse created = courseService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/courses/" + created.id())).body(created);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping
    public PageResponse<CourseSummaryResponse> search(
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "courseCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return courseService.search(status, departmentId, search, pageable);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/by-code/{courseCode}")
    public CourseResponse findByCourseCode(@PathVariable String courseCode) {
        return courseService.findByCourseCode(courseCode);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/assigned-lecturers")
    public List<AssignedLecturerResponse> assignedLecturers() {
        return courseService.listAssignedLecturers();
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/assigned-lecturers/{userId}/sections")
    public List<CourseSectionResponse> lecturerSections(@PathVariable UUID userId) {
        return courseService.listLecturerSections(userId);
    }

    /**
     * Every section running in a term.
     *
     * <p>For staff tools that work across a whole term — the exam timetable, chiefly — rather than
     * course by course. Declared before {@code /sections/{sectionId}} so the literal path is not
     * swallowed by the template.
     */
    @AccessClass(STAFF_ONLY)
    @GetMapping("/sections")
    public List<CourseSectionResponse> sectionsInTerm(@RequestParam UUID academicTermId) {
        return courseService.sectionsInTerm(academicTermId);
    }


    @AccessClass(AUTHENTICATED)
    @GetMapping("/sections/{sectionId}")
    public CourseSectionResponse findSection(@PathVariable UUID sectionId) {
        return courseService.findSection(sectionId);
    }

    @AccessClass(AUTHENTICATED)
    @GetMapping("/{id}")
    public CourseResponse findById(@PathVariable UUID id) {
        return courseService.findById(id);
    }

    @AccessClass(STAFF_ONLY)
    @PatchMapping("/{id}")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.update(id, request);
    }

    @AccessClass(STAFF_ONLY)
    @PutMapping("/{id}/requirements")
    public CourseResponse replaceRequirements(
            @PathVariable UUID id, @Valid @RequestBody ReplaceCourseRequirementsRequest request) {
        return courseService.replaceRequirements(id, request);
    }

    // ---------------------------------------------------------------------
    // Sections — nested under their course, which is the resource that owns them
    // ---------------------------------------------------------------------

    @AccessClass(STAFF_ONLY)
    @PostMapping("/{id}/sections")
    public ResponseEntity<CourseSectionResponse> addSection(
            @PathVariable UUID id, @Valid @RequestBody CreateCourseSectionRequest request) {
        CourseSectionResponse created = courseService.addSection(id, request);
        return ResponseEntity.created(URI.create("/api/v1/courses/" + id + "/sections/" + created.id()))
                .body(created);
    }

    /**
     * Not paged: the number of sections for one course is inherently small and bounded by the
     * timetable, unlike the catalog itself.
     */
    @AccessClass(AUTHENTICATED)
    @GetMapping("/{id}/sections")
    public List<CourseSectionResponse> findSections(@PathVariable UUID id) {
        return courseService.findSections(id);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/sections/{sectionId}/open")
    public CourseSectionResponse openSection(@PathVariable UUID sectionId) {
        return courseService.openSection(sectionId);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/sections/{sectionId}/close")
    public CourseSectionResponse closeSection(@PathVariable UUID sectionId) {
        return courseService.closeSection(sectionId);
    }

    @AccessClass(STAFF_ONLY)
    @PostMapping("/sections/{sectionId}/cancel")
    public CourseSectionResponse cancelSection(@PathVariable UUID sectionId) {
        return courseService.cancelSection(sectionId);
    }

    @AccessClass(STAFF_ONLY)
    @PatchMapping("/sections/{sectionId}")
    public CourseSectionResponse updateSection(
            @PathVariable UUID sectionId, @Valid @RequestBody UpdateSectionRequest request) {
        return courseService.updateSection(sectionId, request);
    }

    @AccessClass(STAFF_ONLY)
    @DeleteMapping("/sections/{sectionId}")
    public ResponseEntity<Void> deleteSection(@PathVariable UUID sectionId) {
        courseService.deleteSection(sectionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Asks whether a lecturer or the rooms would clash, before anything is saved.
     *
     * <p>Read-only despite being a POST: it carries a proposed timetable in the body. Nothing is
     * written and nothing is held, so the write path checks again — between this answer and the save,
     * somebody else may take the room.
     */
    @AccessClass(STAFF_ONLY)
    @PostMapping("/sections/{sectionId}/schedule-check")
    public ScheduleCheckResponse checkSchedule(
            @PathVariable UUID sectionId, @Valid @RequestBody ScheduleCheckRequest request) {
        return courseService.checkSchedule(sectionId, request);
    }


    @AccessClass(STAFF_ONLY)
    @PostMapping("/sections/{sectionId}/lecturer")
    public CourseSectionResponse assignLecturer(
            @PathVariable UUID sectionId, @Valid @RequestBody AssignLecturerRequest request) {
        return courseService.assignLecturer(sectionId, request.lecturerUserId(), request.overrideRequested());
    }

    @AccessClass(STAFF_ONLY)
    @PutMapping("/sections/{sectionId}/meetings")
    public CourseSectionResponse replaceMeetings(
            @PathVariable UUID sectionId, @Valid @RequestBody ReplaceSectionMeetingsRequest request) {
        return courseService.replaceMeetings(sectionId, request);
    }
}
