package com.university.lms.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.DocumentType;
import com.university.lms.document.domain.StorageProvider;
import com.university.lms.document.dto.CreateDocumentRequest;
import com.university.lms.document.repository.DocumentRepository;
import com.university.lms.document.storage.BlobStore;
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
 * A5: {@code DocumentService.downloadForStaff}/{@code findMetadata}/{@code register} narrowed from
 * a blind {@code isStaff()} using a different resolution path than the course-scoped guards — a
 * document has no course or department of its own, so this resolves owner &rarr; student record
 * &rarr; programme &rarr; department instead. Same fail-open safety property throughout.
 */
@ExtendWith(MockitoExtension.class)
class DocumentAccessTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private BlobStore blobStore;

    @Mock
    private StaffAppointments staffAppointments;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private AcademicStructure academicStructure;

    private DocumentService service;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PROGRAMME_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID ORG_UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                documentRepository,
                currentUserProvider,
                userDirectory,
                blobStore,
                new StorageProperties(null, 0),
                staffAppointments,
                studentDirectory,
                academicStructure);
        Document document = new Document(
                DocumentType.TRANSCRIPT, "t.pdf", "application/pdf", 10, "key", StorageProvider.LOCAL_FILESYSTEM, OWNER_ID);
        org.mockito.Mockito.lenient().when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
    }

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("fails open: a staff caller with no appointment at all is authorized")
    void noAppointmentDataFailsOpen() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        assertThat(service.findMetadata(DOCUMENT_ID)).isPresent();
    }

    @Test
    @DisplayName("fails open: the document's owner is not a student — a staff member's own document, for instance")
    void ownerNotAStudentFailsOpen() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "UNIV", "REGISTRAR")));
        when(studentDirectory.studentIdOfUser(OWNER_ID)).thenReturn(Optional.empty());

        assertThat(service.findMetadata(DOCUMENT_ID)).isPresent();
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment elsewhere is refused")
    void fullyProvisionedAndNotAppointedIsRefused() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "REGISTRAR")));
        when(studentDirectory.studentIdOfUser(OWNER_ID)).thenReturn(Optional.of(STUDENT_ID));
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, OWNER_ID, "S12345", PROGRAMME_ID, null, true, null)));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);

        assertThat(service.findMetadata(DOCUMENT_ID)).isEmpty();
        assertThat(service.downloadForStaff(DOCUMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("real narrowing: fully provisioned data and an actual appointment over the owner's department is authorized")
    void fullyProvisionedAndAppointedIsAuthorized() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "REGISTRAR")));
        when(studentDirectory.studentIdOfUser(OWNER_ID)).thenReturn(Optional.of(STUDENT_ID));
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, OWNER_ID, "S12345", PROGRAMME_ID, null, true, null)));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);
        when(blobStore.get("key")).thenReturn("content".getBytes());

        assertThat(service.findMetadata(DOCUMENT_ID)).isPresent();
        assertThat(service.downloadForStaff(DOCUMENT_ID)).isPresent();
    }

    @Test
    @DisplayName("SYSTEM_ADMIN always has access, regardless of appointment data")
    void systemAdminAlwaysAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.SYSTEM_ADMIN));

        assertThat(service.findMetadata(DOCUMENT_ID)).isPresent();
    }

    @Test
    @DisplayName("a non-staff caller never sees the document through the staff path")
    void nonStaffCallerNeverAuthorized() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.STUDENT));

        assertThat(service.findMetadata(DOCUMENT_ID)).isEmpty();
    }

    private static CreateDocumentRequest registerRequestFor(UUID ownerUserId) {
        return new CreateDocumentRequest(
                ownerUserId, DocumentType.TRANSCRIPT, "t.pdf", "application/pdf", 10L, "new-key",
                StorageProvider.LOCAL_FILESYSTEM);
    }

    @Test
    @DisplayName("register: a non-staff caller is refused")
    void registerRefusesNonStaff() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.STUDENT));

        assertThatThrownBy(() -> service.register(registerRequestFor(OWNER_ID)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("register: real narrowing — fully provisioned data and an actual appointment elsewhere is refused")
    void registerRefusesStaffAppointedElsewhere() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(UUID.randomUUID(), "OTHER-DEPT", "REGISTRAR")));
        when(studentDirectory.studentIdOfUser(OWNER_ID)).thenReturn(Optional.of(STUDENT_ID));
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, OWNER_ID, "S12345", PROGRAMME_ID, null, true, null)));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.register(registerRequestFor(OWNER_ID)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("register: fully provisioned data and an actual appointment over the owner's department is authorized")
    void registerAllowsStaffAppointedOverTheDepartment() {
        UUID callerId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(
                callerId, "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
        when(currentUserProvider.require()).thenReturn(caller);
        when(staffAppointments.activeAppointmentsOf(callerId))
                .thenReturn(List.of(new StaffAppointments.Appointment(ORG_UNIT_ID, "DEPT:CS", "REGISTRAR")));
        when(studentDirectory.studentIdOfUser(OWNER_ID)).thenReturn(Optional.of(STUDENT_ID));
        when(studentDirectory.findById(STUDENT_ID))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        STUDENT_ID, OWNER_ID, "S12345", PROGRAMME_ID, null, true, null)));
        when(academicStructure.departmentOfProgramme(PROGRAMME_ID)).thenReturn(Optional.of(DEPARTMENT_ID));
        when(staffAppointments.orgUnitFor("DEPARTMENT", DEPARTMENT_ID)).thenReturn(Optional.of(ORG_UNIT_ID));
        when(staffAppointments.isAppointedOver(callerId, ORG_UNIT_ID)).thenReturn(true);
        when(userDirectory.exists(OWNER_ID)).thenReturn(true);
        when(documentRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.register(registerRequestFor(OWNER_ID))).isNotNull();
    }

    @Test
    @DisplayName("register: fails open when the caller has no appointment data yet")
    void registerFailsOpenWithNoAppointmentData() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.REGISTRAR));
        when(staffAppointments.activeAppointmentsOf(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(userDirectory.exists(OWNER_ID)).thenReturn(true);
        when(documentRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.register(registerRequestFor(OWNER_ID))).isNotNull();
    }
}
