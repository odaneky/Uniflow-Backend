package com.university.lms.finance.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.finance.api.PaymentStanding;
import com.university.lms.finance.api.StudentBilling;
import com.university.lms.finance.api.StudentBilling.TuitionQuote;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FeeApplicability;
import com.university.lms.finance.domain.FeeAssessment;
import com.university.lms.finance.domain.FeeCatalogItem;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.FeeCatalogRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posts the tuition and fee entries that follow from registration. */
@Service
@Transactional
public class DefaultStudentBilling implements StudentBilling {

    private final StudentAccountRepository accountRepository;
    private final AccountEntryRepository entryRepository;
    private final PaymentPlanService paymentPlanService;
    private final TuitionScheduleService tuitionScheduleService;
    private final FeeCatalogService feeCatalogService;
    private final FeeCatalogRepository feeRepository;
    private final StudentDirectory studentDirectory;
    private final AcademicStructure academicStructure;
    private final RefundPolicyService refundPolicyService;

    public DefaultStudentBilling(
            StudentAccountRepository accountRepository,
            AccountEntryRepository entryRepository,
            PaymentPlanService paymentPlanService,
            TuitionScheduleService tuitionScheduleService,
            FeeCatalogService feeCatalogService,
            FeeCatalogRepository feeRepository,
            StudentDirectory studentDirectory,
            AcademicStructure academicStructure,
            RefundPolicyService refundPolicyService) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.paymentPlanService = paymentPlanService;
        this.tuitionScheduleService = tuitionScheduleService;
        this.feeCatalogService = feeCatalogService;
        this.feeRepository = feeRepository;
        this.studentDirectory = studentDirectory;
        this.academicStructure = academicStructure;
        this.refundPolicyService = refundPolicyService;
    }

    @Override
    public TuitionQuote quoteFor(UUID studentId) {
        return tuitionScheduleService.quoteFor(studentId).withCatalogFees(feeCatalogService.quotesFor(studentId));
    }

    @Override
    public void chargeForEnrolment(
            UUID studentId,
            UUID enrollmentId,
            UUID academicTermId,
            String courseCode,
            UUID courseId,
            int credits,
            LocalDate dueOn) {
        if (studentId == null || enrollmentId == null || academicTermId == null) {
            return;
        }
        StudentAccount account = requireAccount(studentId);
        if (dueOn != null) {
            account.dueOn(dueOn);
        }
        Instant at = Instant.now();
        TuitionQuote quote = tuitionScheduleService.quoteFor(studentId);
        String tuitionRef = tuitionReference(enrollmentId);
        if (!entryRepository.existsByAccountIdAndReference(account.getId(), tuitionRef)) {
            BigDecimal amount = quote.amountPerCredit().multiply(BigDecimal.valueOf(Math.max(credits, 0)));
            entryRepository.save(new AccountEntry(
                    account,
                    AccountEntryType.CHARGE,
                    amount,
                    "Tuition — " + courseCode,
                    at,
                    tuitionRef,
                    academicTermId));
        }
        String feeRef = campusFeeReference(academicTermId);
        if (!entryRepository.existsByAccountIdAndReference(account.getId(), feeRef)) {
            entryRepository.save(new AccountEntry(
                    account,
                    AccountEntryType.CHARGE,
                    quote.campusFee(),
                    "Campus fee",
                    at,
                    feeRef,
                    academicTermId));
        }
        UUID programmeId =
                studentDirectory.findById(studentId).map(StudentDirectory.StudentSummary::programmeId).orElse(null);
        for (FeeCatalogItem fee : feeRepository.findAllByOrderByNameAsc()) {
            if (!FeeApplicability.applies(fee, programmeId, courseId)) {
                continue;
            }
            String catalogRef = catalogReference(fee, enrollmentId, academicTermId);
            if (entryRepository.existsByAccountIdAndReference(account.getId(), catalogRef)) {
                continue;
            }
            entryRepository.save(new AccountEntry(
                    account,
                    AccountEntryType.CHARGE,
                    FeeApplicability.amount(fee, credits),
                    fee.getName(),
                    at,
                    catalogRef,
                    academicTermId));
        }
    }

    @Override
    public void creditForDrop(
            UUID studentId, UUID enrollmentId, UUID academicTermId, String courseCode, boolean lastEnrolmentInTerm) {
        if (studentId == null || enrollmentId == null) {
            return;
        }
        StudentAccount account = accountRepository.lockByStudentId(studentId).orElse(null);
        if (account == null) {
            return;
        }
        Instant at = Instant.now();
        reverse(
                account,
                tuitionReference(enrollmentId),
                tuitionCreditReference(enrollmentId),
                "Tuition credit — " + courseCode + " (course drop)",
                at,
                BigDecimal.ONE);
        for (FeeCatalogItem fee : feeRepository.findAll()) {
            reverse(
                    account,
                    catalogEnrolmentReference(fee.getId(), enrollmentId),
                    catalogEnrolmentCreditReference(fee.getId(), enrollmentId),
                    fee.getName() + " credit (course drop)",
                    at,
                    BigDecimal.ONE);
            if (lastEnrolmentInTerm && academicTermId != null && fee.getAssessment() == FeeAssessment.ONCE_PER_TERM) {
                reverse(
                        account,
                        catalogTermReference(fee.getId(), academicTermId),
                        catalogTermCreditReference(fee.getId(), academicTermId),
                        fee.getName() + " credit (course drop)",
                        at,
                        BigDecimal.ONE);
            }
        }
        if (lastEnrolmentInTerm && academicTermId != null) {
            reverse(
                    account,
                    campusFeeReference(academicTermId),
                    campusFeeCreditReference(academicTermId),
                    "Campus fee credit (course drop)",
                    at,
                    BigDecimal.ONE);
        }
    }

    @Override
    public void creditForWithdrawal(
            UUID studentId, UUID enrollmentId, UUID academicTermId, String courseCode, boolean lastEnrolmentInTerm) {
        if (studentId == null || enrollmentId == null) {
            return;
        }
        BigDecimal percent = withdrawalRefundPercent(academicTermId, Instant.now());
        if (percent.signum() <= 0) {
            return;
        }
        StudentAccount account = accountRepository.lockByStudentId(studentId).orElse(null);
        if (account == null) {
            return;
        }
        Instant at = Instant.now();
        String pct = percent.movePointRight(2).stripTrailingZeros().toPlainString();
        reverse(
                account,
                tuitionReference(enrollmentId),
                tuitionCreditReference(enrollmentId),
                "Tuition credit — " + courseCode + " (withdrawal, " + pct + "% refund)",
                at,
                percent);
        for (FeeCatalogItem fee : feeRepository.findAll()) {
            reverse(
                    account,
                    catalogEnrolmentReference(fee.getId(), enrollmentId),
                    catalogEnrolmentCreditReference(fee.getId(), enrollmentId),
                    fee.getName() + " credit (withdrawal, " + pct + "% refund)",
                    at,
                    percent);
            if (lastEnrolmentInTerm && academicTermId != null && fee.getAssessment() == FeeAssessment.ONCE_PER_TERM) {
                reverse(
                        account,
                        catalogTermReference(fee.getId(), academicTermId),
                        catalogTermCreditReference(fee.getId(), academicTermId),
                        fee.getName() + " credit (withdrawal, " + pct + "% refund)",
                        at,
                        percent);
            }
        }
        if (lastEnrolmentInTerm && academicTermId != null) {
            reverse(
                    account,
                    campusFeeReference(academicTermId),
                    campusFeeCreditReference(academicTermId),
                    "Campus fee credit (withdrawal, " + pct + "% refund)",
                    at,
                    percent);
        }
    }

    /**
     * E4: a withdrawal used to earn no refund at all, no matter how soon after add/drop closed it
     * happened. The taper's own dates and percentages are {@link RefundPolicyService} — a settable
     * institution policy, not a hardcoded default — read fresh here so a change takes effect on the
     * next withdrawal without a restart.
     *
     * <p>A drop within the no-penalty window is a full refund handled separately by {@link
     * #creditForDrop}; this only runs once a student is far enough past it that {@code
     * EnrollmentService.requireWithdrawWindow} requires a withdrawal instead of a drop. Zero when
     * the term has no configured add/drop close date to taper from, or when the calendar cannot be
     * resolved at all.
     */
    private BigDecimal withdrawalRefundPercent(UUID academicTermId, Instant asOf) {
        if (academicTermId == null) {
            return BigDecimal.ZERO;
        }
        Optional<AcademicStructure.TermCalendar> calendar = academicStructure.findCalendar(academicTermId, asOf);
        if (calendar.isEmpty() || calendar.get().addDropClosesAt() == null) {
            return BigDecimal.ZERO;
        }
        long daysSinceAddDropClosed = Duration.between(calendar.get().addDropClosesAt(), asOf).toDays();
        if (daysSinceAddDropClosed < 0) {
            // Should not happen — a withdrawal is refused before add/drop closes — but a stale or
            // unexpected calendar lookup must not manufacture a refund no tier actually grants.
            return BigDecimal.ZERO;
        }
        return refundPolicyService.current().percentFor(daysSinceAddDropClosed);
    }

    private void reverse(
            StudentAccount account,
            String chargeRef,
            String creditRef,
            String description,
            Instant at,
            BigDecimal percent) {
        if (entryRepository.existsByAccountIdAndReference(account.getId(), creditRef)) {
            return;
        }
        AccountEntry charge =
                entryRepository.findByAccountIdAndReference(account.getId(), chargeRef).orElse(null);
        if (charge == null) {
            return;
        }
        BigDecimal creditAmount =
                charge.getAmount().abs().multiply(percent).setScale(2, RoundingMode.HALF_UP).negate();
        entryRepository.save(new AccountEntry(
                account,
                AccountEntryType.CREDIT,
                creditAmount,
                description,
                at,
                creditRef));
    }

    private StudentAccount requireAccount(UUID studentId) {
        return accountRepository
                .lockByStudentId(studentId)
                .orElseGet(() -> {
                    try {
                        return accountRepository.saveAndFlush(new StudentAccount(studentId, "USD"));
                    } catch (DataIntegrityViolationException ex) {
                        return accountRepository
                                .lockByStudentId(studentId)
                                .orElseThrow(() -> ex);
                    }
                });
    }

    @Override
    public PaymentStanding standingOf(UUID studentId, UUID academicTermId, LocalDate asOf) {
        return paymentPlanService.standingOf(studentId, academicTermId, asOf);
    }

    static String tuitionReference(UUID enrollmentId) {
        return "tuition:" + enrollmentId;
    }

    static String tuitionCreditReference(UUID enrollmentId) {
        return "tuition-credit:" + enrollmentId;
    }

    static String campusFeeReference(UUID termId) {
        return "campus-fee:" + termId;
    }

    static String campusFeeCreditReference(UUID termId) {
        return "campus-fee-credit:" + termId;
    }

    static String catalogReference(FeeCatalogItem fee, UUID enrollmentId, UUID termId) {
        if (fee.getAssessment() == FeeAssessment.ONCE_PER_TERM) {
            return catalogTermReference(fee.getId(), termId);
        }
        return catalogEnrolmentReference(fee.getId(), enrollmentId);
    }

    static String catalogTermReference(UUID feeId, UUID termId) {
        return "fee:" + feeId + ":term:" + termId;
    }

    static String catalogTermCreditReference(UUID feeId, UUID termId) {
        return "fee-credit:" + feeId + ":term:" + termId;
    }

    static String catalogEnrolmentReference(UUID feeId, UUID enrollmentId) {
        return "fee:" + feeId + ":enrol:" + enrollmentId;
    }

    static String catalogEnrolmentCreditReference(UUID feeId, UUID enrollmentId) {
        return "fee-credit:" + feeId + ":enrol:" + enrollmentId;
    }
}
