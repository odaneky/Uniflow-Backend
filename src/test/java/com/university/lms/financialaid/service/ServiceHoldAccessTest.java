package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.domain.ServiceHold;
import com.university.lms.financialaid.dto.ServiceHoldResponse;
import com.university.lms.financialaid.repository.ServiceHoldRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.student.api.StudentDirectory;
import java.time.Instant;
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
 * A6: {@code ServiceHoldService} dispatches its guard by {@code HoldType} rather than accepting one
 * extra role unconditionally, since holds span several future owners. {@code REGISTRAR} keeps
 * every type, unconditionally, exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class ServiceHoldAccessTest {

    @Mock
    private ServiceHoldRepository repository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ServiceHoldService service;

    private static final UUID STUDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ServiceHoldService(repository, studentDirectory, currentUserProvider);
        org.mockito.Mockito.lenient().when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(repository.save(org.mockito.ArgumentMatchers.any(ServiceHold.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static CurrentUser callerWithRole(String role) {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "caller", "caller@example.edu", "Caller",
                Optional.empty(), Set.of(role), Set.of());
    }

    @Test
    @DisplayName("REGISTRAR may place a hold of any type, unchanged")
    void registrarPlacesAnyHoldType() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.REGISTRAR));

        ServiceHoldResponse response = service.placeHold(STUDENT_ID, HoldType.ORIENTATION, "Missed orientation");

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("BURSAR may place a FINANCIAL hold")
    void bursarPlacesFinancialHold() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.BURSAR));

        ServiceHoldResponse response = service.placeHold(STUDENT_ID, HoldType.FINANCIAL, "Unpaid balance");

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("BURSAR may not place a hold of a type they do not own")
    void bursarCannotPlaceAnAdvisingHold() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.BURSAR));

        assertThatThrownBy(() -> service.placeHold(STUDENT_ID, HoldType.ADVISING, "Missed advising appointment"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("FINANCIAL_AID_OFFICER may place and clear a SAP hold")
    void financialAidOfficerHandlesSapHold() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.FINANCIAL_AID_OFFICER));
        when(repository.findByStudentIdAndActiveTrueOrderByPlacedAtDesc(STUDENT_ID))
                .thenReturn(java.util.List.of(new ServiceHold(
                        STUDENT_ID, HoldType.SAP, "Below SAP threshold", Instant.now(), UUID.randomUUID())));

        ServiceHoldResponse placed = service.placeHold(STUDENT_ID, HoldType.SAP, "Below SAP threshold");
        assertThat(placed).isNotNull();

        service.clearActiveHoldsOfType(STUDENT_ID, HoldType.SAP);
    }

    @Test
    @DisplayName("ACADEMIC_ADVISOR — an existing role with real holders — may place an ADVISING hold")
    void academicAdvisorPlacesAdvisingHold() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.ACADEMIC_ADVISOR));

        ServiceHoldResponse response = service.placeHold(STUDENT_ID, HoldType.ADVISING, "Missed advising appointment");

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("clearHold checks the loaded hold's own type, not a type supplied by the caller")
    void clearHoldChecksTheStoredHoldType() {
        ServiceHold financialHold =
                new ServiceHold(STUDENT_ID, HoldType.FINANCIAL, "Unpaid balance", Instant.now(), UUID.randomUUID());
        when(repository.findById(financialHold.getId())).thenReturn(Optional.of(financialHold));
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.FINANCIAL_AID_OFFICER));

        assertThatThrownBy(() -> service.clearHold(financialHold.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("ORIENTATION, PLACEMENT and MANUAL have no narrower owner and stay registry-only")
    void orientationHoldStaysRegistryOnly() {
        when(currentUserProvider.require()).thenReturn(callerWithRole(SecurityRoles.BURSAR));

        assertThatThrownBy(() -> service.placeHold(STUDENT_ID, HoldType.ORIENTATION, "Missed orientation"))
                .isInstanceOf(ForbiddenException.class);
    }
}
