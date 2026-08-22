package com.university.lms.enrollment.web;

import com.university.lms.enrollment.dto.RosterEntryResponse;
import com.university.lms.enrollment.service.SectionRosterService;
import java.util.List;
import java.util.UUID;
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

    @GetMapping("/{sectionId}/roster")
    public List<RosterEntryResponse> roster(@PathVariable UUID sectionId) {
        return sectionRosterService.roster(sectionId);
    }
}
