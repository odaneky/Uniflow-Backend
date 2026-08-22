package com.university.lms.enrollment.service;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.enrollment.domain.EnrollmentErrorCode;
import com.university.lms.enrollment.dto.RosterEntryResponse;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SectionRosterService {

    private final EnrollmentDirectory enrollmentDirectory;
    private final StudentDirectory studentDirectory;
    private final UserDirectory userDirectory;
    private final CourseCatalog courseCatalog;
    private final CurrentUserProvider currentUserProvider;

    public SectionRosterService(
            EnrollmentDirectory enrollmentDirectory,
            StudentDirectory studentDirectory,
            UserDirectory userDirectory,
            CourseCatalog courseCatalog,
            CurrentUserProvider currentUserProvider) {
        this.enrollmentDirectory = enrollmentDirectory;
        this.studentDirectory = studentDirectory;
        this.userDirectory = userDirectory;
        this.courseCatalog = courseCatalog;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public List<RosterEntryResponse> roster(UUID sectionId) {
        requireStaffForSection(sectionId);
        enrollmentDirectory.reconcileSeatCount(sectionId);
        return enrollmentDirectory.rosterOf(sectionId).stream()
                .map(row -> {
                    StudentDirectory.StudentSummary student = studentDirectory
                            .findById(row.studentId())
                            .orElse(null);
                    if (student == null) {
                        return new RosterEntryResponse(row.studentId(), null, null, null, null, row.status());
                    }
                    var user = userDirectory.findById(student.userId());
                    return new RosterEntryResponse(
                            student.id(),
                            student.userId(),
                            student.studentNumber(),
                            user.map(UserDirectory.UserSummary::fullName).orElse(null),
                            user.map(UserDirectory.UserSummary::email).orElse(null),
                            row.status());
                })
                .toList();
    }

    private void requireStaffForSection(UUID sectionId) {
        CurrentUser caller = currentUserProvider.require();
        CourseCatalog.SectionSummary section = courseCatalog
                .findSection(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EnrollmentErrorCode.ENROLLMENT_SECTION_NOT_FOUND,
                        "No course section exists with id " + sectionId));
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)
                || caller.hasRole(SecurityRoles.ACADEMIC_ADVISOR)) {
            return;
        }
        if (caller.hasRole(SecurityRoles.LECTURER) && caller.userId().equals(section.lecturerUserId())) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }
}
