package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.financialaid.domain.AwardStatus;
import com.university.lms.financialaid.domain.AwardType;
import com.university.lms.financialaid.domain.FinancialAidAward;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.domain.IsirSnapshot;
import com.university.lms.financialaid.domain.ScholarshipProgramme;
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
import org.junit.jupiter.api.DisplayName;
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
    private static final CurrentUser FINANCIAL_AID_OFFICER = new CurrentUser(
            UUID.randomUUID(),
            "sub-faid",
            "financial-aid-officer",
            "financial.aid.officer@university.test",
            "Farah Aid",
            Optional.empty(),
            Set.of(SecurityRoles.FINANCIAL_AID_OFFICER),
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

    @Mock
    private ScholarshipProgrammeService scholarshipProgrammeService;

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
                staffAppointments,
                scholarshipProgrammeService);

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

    @Test
    @DisplayName("A6: FINANCIAL_AID_OFFICER is additionally accepted, alongside REGISTRAR — not instead of it")
    void financialAidOfficerCanPackageAwardsAlongsideRegistrar() {
        lenient().when(currentUserProvider.require()).thenReturn(FINANCIAL_AID_OFFICER);
        when(awardRepository.findByStudentIdAndAcademicTermIdAndAwardType(STUDENT_ID, TERM_ID, AwardType.PELL))
                .thenReturn(Optional.empty());
        when(awardRepository.findByStudentIdAndAcademicTermIdAndAwardType(
                        STUDENT_ID, TERM_ID, AwardType.INSTITUTIONAL))
                .thenReturn(Optional.empty());
        when(awardRepository.save(any(FinancialAidAward.class))).thenAnswer(inv -> inv.getArgument(0));

        List<FinancialAidAwardResponse> awards = service.packageAwards(
                STUDENT_ID, new PackageAwardsRequest(TERM_ID, null, null, new BigDecimal("1200.00")));

        assertThat(awards).hasSize(2);
    }

    private static ScholarshipProgramme activeRenewableProgramme() {
        return new ScholarshipProgramme(
                "Acme Merit Scholarship", "Acme Foundation", null, new BigDecimal("2500.00"), true, null, null);
    }

    @Test
    @DisplayName("E9: awarding a scholarship creates a SCHOLARSHIP award at the programme's default amount")
    void awardingAScholarshipCreatesAnAwardAtTheDefaultAmount() {
        ScholarshipProgramme programme = activeRenewableProgramme();
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);
        when(awardRepository.findByStudentIdAndAcademicTermIdAndScholarshipProgrammeId(
                        STUDENT_ID, TERM_ID, programme.getId()))
                .thenReturn(Optional.empty());
        when(awardRepository.save(any(FinancialAidAward.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialAidAwardResponse response = service.awardScholarship(STUDENT_ID, TERM_ID, programme.getId(), null);

        assertThat(response.awardType()).isEqualTo(AwardType.SCHOLARSHIP);
        assertThat(response.amount()).isEqualByComparingTo("2500.00");
        assertThat(response.scholarshipProgrammeId()).isEqualTo(programme.getId());
    }

    @Test
    @DisplayName("E9: an explicit amount overrides the programme's default")
    void awardingAScholarshipHonorsAnAmountOverride() {
        ScholarshipProgramme programme = activeRenewableProgramme();
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);
        when(awardRepository.findByStudentIdAndAcademicTermIdAndScholarshipProgrammeId(
                        STUDENT_ID, TERM_ID, programme.getId()))
                .thenReturn(Optional.empty());
        when(awardRepository.save(any(FinancialAidAward.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialAidAwardResponse response =
                service.awardScholarship(STUDENT_ID, TERM_ID, programme.getId(), new BigDecimal("4000.00"));

        assertThat(response.amount()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("E9: re-awarding the same programme in the same term is idempotent, not a duplicate")
    void reawardingTheSameProgrammeAndTermDoesNotDuplicate() {
        ScholarshipProgramme programme = activeRenewableProgramme();
        FinancialAidAward existing =
                new FinancialAidAward(STUDENT_ID, TERM_ID, new BigDecimal("2500.00"), programme.getId(), null);
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);
        when(awardRepository.findByStudentIdAndAcademicTermIdAndScholarshipProgrammeId(
                        STUDENT_ID, TERM_ID, programme.getId()))
                .thenReturn(Optional.of(existing));

        FinancialAidAwardResponse response = service.awardScholarship(STUDENT_ID, TERM_ID, programme.getId(), null);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(awardRepository, never()).save(any(FinancialAidAward.class));
    }

    @Test
    @DisplayName("E9: awarding from an inactive programme is refused")
    void awardingFromAnInactiveProgrammeIsRefused() {
        ScholarshipProgramme programme = activeRenewableProgramme();
        programme.deactivate();
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);

        assertThatThrownBy(() -> service.awardScholarship(STUDENT_ID, TERM_ID, programme.getId(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_PROGRAMME_INACTIVE));
        verify(awardRepository, never()).save(any(FinancialAidAward.class));
    }

    @Test
    @DisplayName("E9: renewing carries the prior award's amount and links renewedFromAwardId")
    void renewingCarriesForwardTheAmountAndLink() {
        ScholarshipProgramme programme = activeRenewableProgramme();
        FinancialAidAward prior =
                new FinancialAidAward(STUDENT_ID, TERM_ID, new BigDecimal("2500.00"), programme.getId(), null);
        prior.accept();
        UUID newTermId = UUID.randomUUID();
        when(awardRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);
        lenient()
                .when(academicStructure.findTerm(eq(newTermId), any()))
                .thenReturn(Optional.of(
                        new AcademicStructure.TermSummary(newTermId, "Spring 2027", UUID.randomUUID(), "2026/2027", true)));
        when(awardRepository.findByStudentIdAndAcademicTermIdAndScholarshipProgrammeId(
                        STUDENT_ID, newTermId, programme.getId()))
                .thenReturn(Optional.empty());
        when(awardRepository.save(any(FinancialAidAward.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialAidAwardResponse response = service.renewScholarship(prior.getId(), newTermId, null);

        assertThat(response.amount()).isEqualByComparingTo("2500.00");
        assertThat(response.renewedFromAwardId()).isEqualTo(prior.getId());
        assertThat(response.academicTermId()).isEqualTo(newTermId);
    }

    @Test
    @DisplayName("E9: renewing an award that was never accepted is refused")
    void renewingAnUnacceptedAwardIsRefused() {
        ScholarshipProgramme programme = activeRenewableProgramme();
        FinancialAidAward prior =
                new FinancialAidAward(STUDENT_ID, TERM_ID, new BigDecimal("2500.00"), programme.getId(), null);
        when(awardRepository.findById(prior.getId())).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.renewScholarship(prior.getId(), UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_RENEWAL_INVALID_STATE));
    }

    @Test
    @DisplayName("E9: renewing a non-scholarship award is refused")
    void renewingANonScholarshipAwardIsRefused() {
        FinancialAidAward prior =
                new FinancialAidAward(STUDENT_ID, TERM_ID, AwardType.PELL, new BigDecimal("7395.00"), AwardStatus.ACCEPTED);
        when(awardRepository.findById(prior.getId())).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.renewScholarship(prior.getId(), UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_RENEWAL_INVALID_STATE));
    }

    @Test
    @DisplayName("E9: renewing from a non-renewable programme is refused")
    void renewingFromANonRenewableProgrammeIsRefused() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "One-Time Award", null, null, new BigDecimal("1000.00"), false, null, null);
        FinancialAidAward prior =
                new FinancialAidAward(STUDENT_ID, TERM_ID, new BigDecimal("1000.00"), programme.getId(), null);
        prior.accept();
        UUID newTermId = UUID.randomUUID();
        when(awardRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);
        lenient()
                .when(academicStructure.findTerm(eq(newTermId), any()))
                .thenReturn(Optional.of(
                        new AcademicStructure.TermSummary(newTermId, "Spring 2027", UUID.randomUUID(), "2026/2027", true)));

        assertThatThrownBy(() -> service.renewScholarship(prior.getId(), newTermId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_NOT_RENEWABLE));
    }

    @Test
    @DisplayName("E9: renewing past the programme's renewal cap is refused")
    void renewingPastTheRenewalCapIsRefused() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Capped Scholarship", null, null, new BigDecimal("1000.00"), true, 1, null);
        FinancialAidAward original =
                new FinancialAidAward(STUDENT_ID, TERM_ID, new BigDecimal("1000.00"), programme.getId(), null);
        FinancialAidAward onceRenewed = new FinancialAidAward(
                STUDENT_ID, UUID.randomUUID(), new BigDecimal("1000.00"), programme.getId(), original.getId());
        onceRenewed.accept();
        UUID newTermId = UUID.randomUUID();
        when(awardRepository.findById(onceRenewed.getId())).thenReturn(Optional.of(onceRenewed));
        when(awardRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(scholarshipProgrammeService.require(programme.getId())).thenReturn(programme);
        lenient()
                .when(academicStructure.findTerm(eq(newTermId), any()))
                .thenReturn(Optional.of(
                        new AcademicStructure.TermSummary(newTermId, "Spring 2027", UUID.randomUUID(), "2026/2027", true)));

        assertThatThrownBy(() -> service.renewScholarship(onceRenewed.getId(), newTermId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_RENEWAL_LIMIT_REACHED));
    }
}
