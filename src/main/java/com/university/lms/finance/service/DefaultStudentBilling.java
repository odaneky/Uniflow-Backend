package com.university.lms.finance.service;

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
import java.time.Instant;
import java.time.LocalDate;
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

    public DefaultStudentBilling(
            StudentAccountRepository accountRepository,
            AccountEntryRepository entryRepository,
            PaymentPlanService paymentPlanService,
            TuitionScheduleService tuitionScheduleService,
            FeeCatalogService feeCatalogService,
            FeeCatalogRepository feeRepository,
            StudentDirectory studentDirectory) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.paymentPlanService = paymentPlanService;
        this.tuitionScheduleService = tuitionScheduleService;
        this.feeCatalogService = feeCatalogService;
        this.feeRepository = feeRepository;
        this.studentDirectory = studentDirectory;
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
                    tuitionRef));
        }
        String feeRef = campusFeeReference(academicTermId);
        if (!entryRepository.existsByAccountIdAndReference(account.getId(), feeRef)) {
            entryRepository.save(new AccountEntry(
                    account, AccountEntryType.CHARGE, quote.campusFee(), "Campus fee", at, feeRef));
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
                    catalogRef));
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
                at);
        for (FeeCatalogItem fee : feeRepository.findAll()) {
            reverse(
                    account,
                    catalogEnrolmentReference(fee.getId(), enrollmentId),
                    catalogEnrolmentCreditReference(fee.getId(), enrollmentId),
                    fee.getName() + " credit (course drop)",
                    at);
            if (lastEnrolmentInTerm && academicTermId != null && fee.getAssessment() == FeeAssessment.ONCE_PER_TERM) {
                reverse(
                        account,
                        catalogTermReference(fee.getId(), academicTermId),
                        catalogTermCreditReference(fee.getId(), academicTermId),
                        fee.getName() + " credit (course drop)",
                        at);
            }
        }
        if (lastEnrolmentInTerm && academicTermId != null) {
            reverse(
                    account,
                    campusFeeReference(academicTermId),
                    campusFeeCreditReference(academicTermId),
                    "Campus fee credit (course drop)",
                    at);
        }
    }

    private void reverse(
            StudentAccount account, String chargeRef, String creditRef, String description, Instant at) {
        if (entryRepository.existsByAccountIdAndReference(account.getId(), creditRef)) {
            return;
        }
        AccountEntry charge =
                entryRepository.findByAccountIdAndReference(account.getId(), chargeRef).orElse(null);
        if (charge == null) {
            return;
        }
        entryRepository.save(new AccountEntry(
                account,
                AccountEntryType.CREDIT,
                charge.getAmount().abs().negate(),
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
