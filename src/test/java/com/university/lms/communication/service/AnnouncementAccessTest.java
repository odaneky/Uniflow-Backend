package com.university.lms.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.outbox.OutboxWriter;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.communication.api.CommsRateLimiter;
import com.university.lms.communication.api.MessagingPolicy;
import com.university.lms.communication.domain.Announcement;
import com.university.lms.communication.domain.AnnouncementAudience;
import com.university.lms.communication.dto.AnnouncementResponse;
import com.university.lms.communication.dto.CreateAnnouncementRequest;
import com.university.lms.communication.repository.AnnouncementReadRepository;
import com.university.lms.communication.repository.AnnouncementRepository;
import com.university.lms.communication.repository.ConversationParticipantRepository;
import com.university.lms.communication.repository.ConversationRepository;
import com.university.lms.communication.repository.MessageRepository;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A5: {@code CommunicationService.createAnnouncement}/{@code publishAnnouncement} narrowed so any
 * staff role can no longer broadcast to a faculty, department, programme or course section with no
 * relationship to it. {@code UNIVERSITY_WIDE} is deliberately unaffected — see {@code
 * resolveOrgUnitForAudience}'s javadoc.
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementAccessTest {

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private AnnouncementReadRepository announcementReadRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private EnrollmentDirectory enrollmentDirectory;

    @Mock
    private MessagingPolicy messagingPolicy;

    @Mock
    private CommsRateLimiter commsRateLimiter;

    @Mock
    private DocumentStore documentStore;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private CourseCatalog courseCatalog;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private StaffAppointments staffAppointments;

    private CommunicationService service;

    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommunicationService(
                announcementRepository,
                announcementReadRepository,
                conversationRepository,
                participantRepository,
                messageRepository,
                currentUserProvider,
                userDirectory,
                studentDirectory,
                enrollmentDirectory,
                messagingPolicy,
                commsRateLimiter,
                documentStore,
                auditTrail,
                outboxWriter,
                new ObjectMapper(),
                courseCatalog,
                academicStructure,
                staffAppointments);
        org.mockito.Mockito.lenient()
                .when(announcementRepository.save(any(Announcement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    private static CreateAnnouncementRequest departmentAnnouncement() {
        return new CreateAnnouncementRequest("Title", "Body", AnnouncementAudience.DEPARTMENT, DEPARTMENT_ID, false);
    }

    @Test
    @DisplayName("fails open: a staff caller with no appointment at all is authorized")
    void noAppointmentDataFailsOpen() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.LECTURER));
        when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());

        AnnouncementResponse response = service.createAnnouncement(departmentAnnouncement());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("UNIVERSITY_WIDE is unaffected by org-scoping regardless of appointment data")
    void universityWideIsUnaffected() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.LECTURER), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "SOME-DEPT", "LECTURER")));

        AnnouncementResponse response = service.createAnnouncement(
                new CreateAnnouncementRequest("Title", "Body", AnnouncementAudience.UNIVERSITY_WIDE, null, false));

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment elsewhere is refused")
    void fullyProvisionedAndNotAppointedIsRefused() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.LECTURER), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "LECTURER")));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createAnnouncement(departmentAnnouncement()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment over this department is authorized")
    void fullyProvisionedAndAppointedIsAuthorized() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.LECTURER), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "LECTURER")));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);

        AnnouncementResponse response = service.createAnnouncement(departmentAnnouncement());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));

        AnnouncementResponse response = service.createAnnouncement(departmentAnnouncement());

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("a non-staff caller is refused before any org-scope resolution happens")
    void nonStaffCallerRefused() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.STUDENT));

        assertThatThrownBy(() -> service.createAnnouncement(departmentAnnouncement()))
                .isInstanceOf(ForbiddenException.class);
    }
}
