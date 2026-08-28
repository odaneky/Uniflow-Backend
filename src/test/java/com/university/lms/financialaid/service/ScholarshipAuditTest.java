package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.administration.service.AuditableAspect;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.financialaid.domain.FinancialAidAward;
import com.university.lms.financialaid.domain.ScholarshipProgramme;
import com.university.lms.financialaid.dto.CreateScholarshipProgrammeRequest;
import com.university.lms.financialaid.dto.FinancialAidAwardResponse;
import com.university.lms.financialaid.dto.ScholarshipProgrammeResponse;
import com.university.lms.financialaid.repository.FinancialAidAwardRepository;
import com.university.lms.financialaid.repository.IsirSnapshotRepository;
import com.university.lms.financialaid.repository.ScholarshipProgrammeRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
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
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * E9: pins the SpEL in {@code ScholarshipProgrammeService}'s and {@code FinancialAidService}'s new
 * {@code @Auditable} annotations specifically — same reasoning and shape as {@code
 * ServiceHoldAuditTest}: a typo in an expression is only caught by a test that actually resolves it
 * through a real {@code AuditableAspect} proxy, since a broken expression fails silently at runtime.
 */
@ExtendWith(MockitoExtension.class)
class ScholarshipAuditTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TERM_ID = UUID.randomUUID();

    @Mock
    private ScholarshipProgrammeRepository programmeRepository;

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
    private AuditTrail auditTrail;

    private ScholarshipProgrammeService programmeProxy;
    private FinancialAidService financialAidProxy;

    @BeforeEach
    void setUp() {
        AuditableAspect aspect = new AuditableAspect(auditTrail, currentUserProvider);

        ScholarshipProgrammeService programmeTarget = new ScholarshipProgrammeService(programmeRepository);
        AspectJProxyFactory programmeFactory = new AspectJProxyFactory(programmeTarget);
        programmeFactory.setProxyTargetClass(true);
        programmeFactory.addAspect(aspect);
        programmeProxy = programmeFactory.getProxy();

        FinancialAidService financialAidTarget = new FinancialAidService(
                isirRepository,
                awardRepository,
                accountRepository,
                entryRepository,
                studentDirectory,
                academicStructure,
                currentUserProvider,
                staffAppointments,
                programmeProxy);
        AspectJProxyFactory financialAidFactory = new AspectJProxyFactory(financialAidTarget);
        financialAidFactory.setProxyTargetClass(true);
        financialAidFactory.addAspect(aspect);
        financialAidProxy = financialAidFactory.getProxy();

        lenient().when(currentUserProvider.require()).thenReturn(registrar());
        lenient().when(staffAppointments.activeAppointmentsOf(any())).thenReturn(List.of());
        lenient().when(studentDirectory.exists(STUDENT_ID)).thenReturn(true);
        lenient()
                .when(academicStructure.findTerm(eq(TERM_ID), any()))
                .thenReturn(Optional.of(
                        new AcademicStructure.TermSummary(TERM_ID, "Fall 2026", UUID.randomUUID(), "2026/2027", true)));
        lenient().when(programmeRepository.save(any(ScholarshipProgramme.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(awardRepository.save(any(FinancialAidAward.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CurrentUser registrar() {
        return new CurrentUser(
                UUID.randomUUID(), "idp-subject", "registrar", "registrar@example.edu", "Rita Registrar",
                Optional.empty(), Set.of(SecurityRoles.REGISTRAR), Set.of());
    }

    @Test
    @DisplayName("ScholarshipProgrammeService.create: entityId comes from #result.id()")
    void createResolvesEntityIdFromTheResponseRecord() {
        when(programmeRepository.existsByNameIgnoreCase("Acme Merit Scholarship")).thenReturn(false);

        ScholarshipProgrammeResponse response = programmeProxy.create(new CreateScholarshipProgrammeRequest(
                "Acme Merit Scholarship", null, null, new BigDecimal("2500.00"), false, null, null));

        verify(auditTrail).record(
                any(), eq("Rita Registrar"), eq(AuditTrail.Action.SCHOLARSHIP_PROGRAMME_CREATED),
                eq(AuditTrail.EntityType.SCHOLARSHIP_PROGRAMME), eq(response.id()), eq("Acme Merit Scholarship"));
    }

    @Test
    @DisplayName("ScholarshipProgrammeService.deactivate: entityId comes directly from the #programmeId parameter")
    void deactivateResolvesEntityIdFromTheParameter() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Acme Merit Scholarship", null, null, new BigDecimal("2500.00"), false, null, null);
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));

        programmeProxy.deactivate(programme.getId());

        verify(auditTrail).record(
                any(), any(), eq(AuditTrail.Action.SCHOLARSHIP_PROGRAMME_DEACTIVATED),
                eq(AuditTrail.EntityType.SCHOLARSHIP_PROGRAMME), eq(programme.getId()), any());
    }

    @Test
    @DisplayName("FinancialAidService.awardScholarship: entityId comes from #result.id() on the response record")
    void awardScholarshipResolvesEntityIdFromTheResponseRecord() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Acme Merit Scholarship", null, null, new BigDecimal("2500.00"), true, null, null);
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        when(awardRepository.findByStudentIdAndAcademicTermIdAndScholarshipProgrammeId(
                        STUDENT_ID, TERM_ID, programme.getId()))
                .thenReturn(Optional.empty());

        FinancialAidAwardResponse response =
                financialAidProxy.awardScholarship(STUDENT_ID, TERM_ID, programme.getId(), null);

        verify(auditTrail).record(
                any(), eq("Rita Registrar"), eq(AuditTrail.Action.SCHOLARSHIP_AWARDED),
                eq(AuditTrail.EntityType.FINANCIAL_AID_AWARD), eq(response.id()), any());
    }

    @Test
    @DisplayName("FinancialAidService.renewScholarship: entityId comes from #result.id() on the response record")
    void renewScholarshipResolvesEntityIdFromTheResponseRecord() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Acme Merit Scholarship", null, null, new BigDecimal("2500.00"), true, null, null);
        FinancialAidAward prior =
                new FinancialAidAward(STUDENT_ID, TERM_ID, new BigDecimal("2500.00"), programme.getId(), null);
        prior.accept();
        UUID newTermId = UUID.randomUUID();
        when(awardRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(programmeRepository.findById(programme.getId())).thenReturn(Optional.of(programme));
        lenient()
                .when(academicStructure.findTerm(eq(newTermId), any()))
                .thenReturn(Optional.of(
                        new AcademicStructure.TermSummary(newTermId, "Spring 2027", UUID.randomUUID(), "2026/2027", true)));
        when(awardRepository.findByStudentIdAndAcademicTermIdAndScholarshipProgrammeId(
                        STUDENT_ID, newTermId, programme.getId()))
                .thenReturn(Optional.empty());

        FinancialAidAwardResponse response = financialAidProxy.renewScholarship(prior.getId(), newTermId, null);

        verify(auditTrail).record(
                any(), eq("Rita Registrar"), eq(AuditTrail.Action.SCHOLARSHIP_RENEWED),
                eq(AuditTrail.EntityType.FINANCIAL_AID_AWARD), eq(response.id()), any());
    }
}
