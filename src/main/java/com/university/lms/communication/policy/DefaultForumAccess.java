package com.university.lms.communication.policy;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.api.ForumAccess;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.student.api.StudentDirectory;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultForumAccess implements ForumAccess {

    private final StudentDirectory studentDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CourseCatalog courseCatalog;

    public DefaultForumAccess(
            StudentDirectory studentDirectory,
            EnrollmentDirectory enrollmentDirectory,
            CourseCatalog courseCatalog) {
        this.studentDirectory = studentDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.courseCatalog = courseCatalog;
    }

    @Override
    public void assertCanReadForum(CurrentUser caller, UUID courseSectionId) {
        if (canRead(caller, courseSectionId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }

    @Override
    public void assertCanPostForum(CurrentUser caller, UUID courseSectionId) {
        assertCanReadForum(caller, courseSectionId);
    }

    @Override
    public void assertCanModerateForum(CurrentUser caller, UUID courseSectionId) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)
                || courseCatalog.teaches(caller.userId(), courseSectionId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }

    private boolean canRead(CurrentUser caller, UUID courseSectionId) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)
                || caller.hasRole(SecurityRoles.REGISTRAR)
                || caller.hasRole(SecurityRoles.FACULTY_ADMIN)) {
            return true;
        }
        if (courseCatalog.teaches(caller.userId(), courseSectionId)) {
            return true;
        }
        return studentDirectory
                .studentIdOfUser(caller.userId())
                .map(studentId -> enrollmentDirectory.canAccessLearning(studentId, courseSectionId))
                .orElse(false);
    }
}
