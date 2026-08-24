package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.financialaid.domain.AwardStatus;
import com.university.lms.financialaid.domain.AwardType;
import com.university.lms.financialaid.domain.FinancialAidAward;
import com.university.lms.financialaid.domain.IsirSnapshot;
import com.university.lms.financialaid.dto.FinancialAidAwardResponse;
import com.university.lms.financialaid.dto.PackageAwardsRequest;
import com.university.lms.financialaid.repository.FinancialAidAwardRepository;
import com.university.lms.financialaid.repository.IsirSnapshotRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code packageAwards} used to save unconditionally: a retried packaging call — a client timeout,
 * a double submit of the form — created a second PELL and/or INSTITUTIONAL award for the same
 * student and term. These tests pin the fix: at most one award per (student, term, type), backed at
 * the database by {@code uk_financial_aid_awards_student_term_type} (V63).
 */
@ExtendWith(MockitoExtension.class)
class FinancialAidServiceTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TERM_ID = UUID.randomUUID();
    private static final CurrentUser REGISTRAR = new CurrentUser(
            UUID.randomUUID(),
            "sub",
            "registrar",
            "registrar@university.test",
            "Rita Registrar",
            Optional.empty(),
            Set.of(SecurityRoles.REGISTRAR),
            Set.of());

    @Mock
    private IsirSnapshotRepository isirRepository;

    @Mock
    private FinancialAidAwardRepository awardRepository;

    @Mock
    private StudentAccountRepository accountRepository;

    @Mock
    private AccountEntryRepository entryRepository;

    @Mock
    private StudentDirectory studentDirectory;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private StaffAppointments staffAppointments;

    private FinancialAidService service;

    @BeforeEach
    void setUp() {
        service = new FinancialAidService(
                isirRepository,
                awardRepository,
                accountRepository,
                entryRepository,
                studentDirectory,
                academicStructure,
                currentUserProvider,
                staffAppointments);

        lenient().when(currentUserProvider.require()).thenReturn(REGISTRAR);
        lenient().when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());
        lenient().when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        lenient()
                .when(academicStructure.findTerm(eq(TERM_ID), any()))
                .thenReturn(Optional.of(new AcademicStructure.TermSummary(TERM_ID, "Fall 2026", UUID.randomUUID(), "2026/2027", true)));
        lenient()
                .when(isirRepository.findByStudentIdAndAidYear(eq(STUDENT_ID), any()))
                .thenReturn(Optional.of(
                        new IsirSnapshot(STUDENT_ID, "2026-2027", new BigDecimal("0"), true, "{}", Instant.now())));
    }

    @Test
    void packagingOnceCreatesPellAndInstitutionalAwards() {
        when(awardRepository.findByStudentIdAndAcademicTermIdAndAwardType(STUDENT_ID, TERM_ID, AwardType.PELL))
                .thenReturn(Optional.empty());
        when(awardRepository.findByStudentIdAndAcademicTermIdAndAwardType(
                        STUDENT_ID, TERM_ID, AwardType.INSTITUTIONAL))
                .thenReturn(Optional.empty());
        when(awardRepository.save(any(FinancialAidAward.class))).thenAnswer(inv -> inv.getArgument(0));

        List<FinancialAidAwardResponse> awards = service.packageAwards(
                STUDENT_ID, new PackageAwardsRequest(TERM_ID, null, null, new BigDecimal("1200.00")));

        assertThat(awards).hasSize(2);
        assertThat(awards).extracting(FinancialAidAwardResponse::awardType)
                .containsExactlyInAnyOrder(AwardType.PELL, AwardType.INSTITUTIONAL);
        verify(awardRepository, times(2)).save(any(FinancialAidAward.class));
    }

    @Test
    void repackagingTheSameStudentAndTermDoesNotDuplicateAwards() {
        FinancialAidAward existingPell =
                new FinancialAidAward(STUDENT_ID, TERM_ID, AwardType.PELL, new BigDecimal("7395.00"), AwardStatus.OFFERED);
        FinancialAidAward existingInstitutional = new FinancialAidAward(
                STUDENT_ID, TERM_ID, AwardType.INSTITUTIONAL, new BigDecimal("1200.00"), AwardStatus.OFFERED);
        when(awardRepository.findByStudentIdAndAcademicTermIdAndAwardType(STUDENT_ID, TERM_ID, AwardType.PELL))
                .thenReturn(Optional.of(existingPell));
        when(awardRepository.findByStudentIdAndAcademicTermIdAndAwardType(
                        STUDENT_ID, TERM_ID, AwardType.INSTITUTIONAL))
                .thenReturn(Optional.of(existingInstitutional));

        List<FinancialAidAwardResponse> awards = service.packageAwards(
                STUDENT_ID, new PackageAwardsRequest(TERM_ID, null, null, new BigDecimal("1200.00")));

        assertThat(awards).hasSize(2);
        assertThat(awards).extracting(FinancialAidAwardResponse::id)
                .containsExactlyInAnyOrder(existingPell.getId(), existingInstitutional.getId());
        verify(awardRepository, never()).save(any(FinancialAidAward.class));
    }
}
