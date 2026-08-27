package com.university.lms.enrollment.web;

import com.university.lms.enrollment.dto.RosterEntryResponse;
import com.university.lms.enrollment.service.SectionRosterService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/sections")
public class SectionRosterController {

    private final SectionRosterService sectionRosterService;

    public SectionRosterController(SectionRosterService sectionRosterService) {
        this.sectionRosterService = sectionRosterService;
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping("/{sectionId}/roster")
    public List<RosterEntryResponse> roster(@PathVariable UUID sectionId) {
        return sectionRosterService.roster(sectionId);
    }

    @AccessClass(STAFF_ONLY)
    @GetMapping(value = "/{sectionId}/roster/export", produces = "text/csv")
    public ResponseEntity<String> exportRoster(@PathVariable UUID sectionId) {
        String csv = sectionRosterService.rosterCsv(sectionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"roster-" + sectionId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
