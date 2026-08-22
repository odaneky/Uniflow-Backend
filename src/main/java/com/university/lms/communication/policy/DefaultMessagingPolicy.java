package com.university.lms.communication.policy;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.api.MessagingPolicy;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DefaultMessagingPolicy implements MessagingPolicy {

    private final UserDirectory userDirectory;
    private final StudentDirectory studentDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CourseCatalog courseCatalog;
    private final ConversationParticipantRepository participantRepository;

    public DefaultMessagingPolicy(
            UserDirectory userDirectory,
            StudentDirectory studentDirectory,
            EnrollmentDirectory enrollmentDirectory,
            CourseCatalog courseCatalog,
            ConversationParticipantRepository participantRepository) {
        this.userDirectory = userDirectory;
        this.studentDirectory = studentDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.courseCatalog = courseCatalog;
        this.participantRepository = participantRepository;
    }

    @Override
    public void assertCanStartConversation(
            CurrentUser caller, Set<UUID> participantUserIds, UUID courseSectionId) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return;
        }
        Set<UUID> targets = new HashSet<>(participantUserIds);
        targets.remove(caller.userId());
        if (targets.isEmpty()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "A conversation requires at least one other participant");
        }
        for (UUID targetUserId : targets) {
            if (!userDirectory.exists(targetUserId)) {
                throw new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "Participant " + targetUserId + " does not exist");
            }
            if (!mayMessage(caller, targetUserId, courseSectionId)) {
                throw new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED,
                        "You are not allowed to message this participant");
            }
        }
    }

    @Override
    public void assertCanSendMessage(CurrentUser caller, UUID conversationId) {
        assertCanReadConversation(caller, conversationId);
    }

    @Override
    public void assertCanReadConversation(CurrentUser caller, UUID conversationId) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return;
        }
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, caller.userId())) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    @Override
    public void assertCanAddParticipant(
            CurrentUser caller, UUID conversationId, UUID newParticipantUserId) {
        assertCanReadConversation(caller, conversationId);
        if (!userDirectory.exists(newParticipantUserId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "Participant does not exist");
        }
        // Only staff may expand a thread beyond the original pairwise relationship.
        if (!caller.isStaff()) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You are not allowed to add participants");
        }
    }

    private boolean mayMessage(CurrentUser caller, UUID targetUserId, UUID courseSectionId) {
        if (caller.userId().equals(targetUserId)) {
            return false;
        }
        if (caller.isStaff()) {
            return staffMayMessageStudent(caller, targetUserId, courseSectionId)
                    || staffMayMessageStaff(caller, targetUserId);
        }
        return studentMayMessage(caller, targetUserId, courseSectionId);
    }

    private boolean studentMayMessage(CurrentUser student, UUID targetUserId, UUID courseSectionId) {
        if (studentDirectory.advisorUserIdOf(student.userId()).filter(targetUserId::equals).isPresent()) {
            return true;
        }
        if (courseSectionId != null
                && courseCatalog.teaches(targetUserId, courseSectionId)
                && studentDirectory
                        .studentIdOfUser(student.userId())
                        .map(studentId -> enrollmentDirectory.canAccessLearning(studentId, courseSectionId))
                        .orElse(false)) {
            return true;
        }
        return false;
    }

    private boolean staffMayMessageStudent(CurrentUser staff, UUID targetUserId, UUID courseSectionId) {
        UUID targetStudentId = studentDirectory.studentIdOfUser(targetUserId).orElse(null);
        if (targetStudentId == null) {
            return false;
        }
        if (staff.hasRole(SecurityRoles.ACADEMIC_ADVISOR)
                && studentDirectory.adviseeUserIdsOf(staff.userId()).contains(targetUserId)) {
            return true;
        }
        if (staff.hasRole(SecurityRoles.LECTURER) && courseSectionId != null) {
            if (courseCatalog.teaches(staff.userId(), courseSectionId)
                    && enrollmentDirectory.canAccessLearning(targetStudentId, courseSectionId)) {
                return true;
            }
        }
        if (staff.hasRole(SecurityRoles.REGISTRAR) || staff.hasRole(SecurityRoles.FACULTY_ADMIN)) {
            return true;
        }
        if (staff.hasRole(SecurityRoles.LECTURER)) {
            return lecturerTeachesAnySharedSection(staff.userId(), targetStudentId);
        }
        return false;
    }

    private boolean lecturerTeachesAnySharedSection(UUID lecturerUserId, UUID targetStudentId) {
        List<UUID> sections = enrollmentDirectory.accessibleSectionIds(targetStudentId);
        for (UUID sectionId : sections) {
            if (courseCatalog.teaches(lecturerUserId, sectionId)) {
                return true;
            }
        }
        return false;
    }

    private boolean staffMayMessageStaff(CurrentUser staff, UUID targetUserId) {
        return userDirectory.findById(targetUserId).map(UserDirectory.UserSummary::active).orElse(false)
                && staff.isStaff();
    }
}
