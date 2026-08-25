package com.university.lms.finance.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.finance.api.PaymentStanding;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.FinanceErrorCode;
import com.university.lms.finance.domain.PaymentInstallment;
import com.university.lms.finance.domain.PaymentPlan;
import com.university.lms.finance.domain.PaymentSchedule;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.dto.PaymentPlanResponse;
import com.university.lms.finance.dto.ReplacePaymentPlanRequest;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.PaymentPlanRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentPlanService {

    private final PaymentPlanRepository planRepository;
    private final StudentAccountRepository accountRepository;
    private final AccountEntryRepository entryRepository;
    private final AcademicStructure academicStructure;

    public PaymentPlanService(
            PaymentPlanRepository planRepository,
            StudentAccountRepository accountRepository,
            AccountEntryRepository entryRepository,
            AcademicStructure academicStructure) {
        this.planRepository = planRepository;
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.academicStructure = academicStructure;
    }

    public PaymentPlanResponse find(UUID academicTermId) {
        AcademicStructure.TermCalendar term = requireTerm(academicTermId);
        List<PaymentPlanResponse.InstallmentResponse> rows = planRepository
                .findByAcademicTermId(academicTermId)
                .map(plan -> plan.getInstallments().stream().map(PaymentPlanService::toResponse).toList())
                .orElse(List.of());
        return new PaymentPlanResponse(term.id(), term.name(), term.startDate(), rows);
    }

    @Auditable(
            action = AuditTrail.Action.PAYMENT_PLAN_REPLACED,
            entityType = AuditTrail.EntityType.PAYMENT_PLAN,
            entityId = "#academicTermId")
    @Transactional
    public PaymentPlanResponse replace(UUID academicTermId, ReplacePaymentPlanRequest request) {
        AcademicStructure.TermCalendar term = requireTerm(academicTermId);
        List<ReplacePaymentPlanRequest.InstallmentRequest> specs = request.installments();
        PaymentPlan plan = planRepository
                .findByAcademicTermId(academicTermId)
                .orElseGet(() -> planRepository.save(new PaymentPlan(academicTermId)));
        if (specs.isEmpty()) {
            plan.replaceInstallments(List.of());
            return new PaymentPlanResponse(term.id(), term.name(), term.startDate(), List.of());
        }
        validatePercents(specs);
        List<PaymentInstallment> next = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            var spec = specs.get(i);
            LocalDate dueOn = resolveDueOn(term.startDate(), spec);
            next.add(new PaymentInstallment(
                    plan,
                    i,
                    spec.label().trim(),
                    spec.cumulativePercent(),
                    spec.weekOfTerm(),
                    dueOn,
                    spec.placesHold() == null || spec.placesHold(),
                    Boolean.TRUE.equals(spec.blocksExams())));
        }
        plan.replaceInstallments(next);
        return new PaymentPlanResponse(
                term.id(), term.name(), term.startDate(), next.stream().map(PaymentPlanService::toResponse).toList());
    }

    public PaymentStanding standingOf(UUID studentId, UUID academicTermId, LocalDate asOf) {
        if (studentId == null) {
            return PaymentStanding.none();
        }
        AcademicStructure.TermCalendar term = academicTermId == null
                ? academicStructure.currentTerm(asOf.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)).orElse(null)
                : academicStructure.findCalendar(academicTermId, asOf.atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
                        .orElse(null);
        if (term == null) {
            return PaymentStanding.none();
        }
        LedgerTotals totals = totalsOf(studentId, term.id());
        List<PaymentSchedule.Step> steps = planRepository
                .findByAcademicTermId(term.id())
                .filter(plan -> !plan.getInstallments().isEmpty())
                .map(plan -> plan.getInstallments().stream()
                        .map(row -> new PaymentSchedule.Step(
                                row.getLabel(),
                                row.getCumulativePercent(),
                                row.getWeekOfTerm(),
                                row.getDueOn(),
                                row.isPlacesHold(),
                                row.isBlocksExams()))
                        .toList())
                .orElseGet(() -> fallbackSteps(term));
        return PaymentSchedule.evaluate(term.id(), steps, totals.charges, totals.paid, asOf);
    }

    private AcademicStructure.TermCalendar requireTerm(UUID academicTermId) {
        return academicStructure
                .findCalendar(academicTermId, java.time.Instant.now())
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinanceErrorCode.PAYMENT_PLAN_TERM_NOT_FOUND,
                        "No academic term exists with id " + academicTermId));
    }

    private LedgerTotals totalsOf(UUID studentId, UUID academicTermId) {
        StudentAccount account = accountRepository.findByStudentId(studentId).orElse(null);
        if (account == null) {
            return new LedgerTotals(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal charges = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        String termToken = academicTermId == null ? null : academicTermId.toString();
        for (AccountEntry entry : entryRepository.findByAccountIdOrderByOccurredAtAsc(account.getId())) {
            if (termToken != null && entry.getReference() != null && !entry.getReference().contains(termToken)) {
                if (entry.getAcademicTermId() != null && !entry.getAcademicTermId().equals(academicTermId)) {
                    continue;
                }
                if (entry.getAcademicTermId() == null
                        && !entry.getReference().contains("term:" + termToken)
                        && !entry.getReference().contains("campus-fee:" + termToken)) {
                    continue;
                }
            }
            if (entry.getEntryType() == AccountEntryType.CHARGE) {
                charges = charges.add(entry.getAmount().abs());
            } else {
                paid = paid.add(entry.getAmount().abs());
            }
        }
        return new LedgerTotals(charges, paid);
    }

    private static List<PaymentSchedule.Step> fallbackSteps(AcademicStructure.TermCalendar term) {
        if (term.tuitionDueOn() == null) {
            return List.of();
        }
        return List.of(new PaymentSchedule.Step(
                "Tuition due", 100, null, term.tuitionDueOn(), true, true));
    }

    private static LocalDate resolveDueOn(LocalDate termStart, ReplacePaymentPlanRequest.InstallmentRequest spec) {
        if (spec.weekOfTerm() != null) {
            return PaymentSchedule.dueBeforeWeek(termStart, spec.weekOfTerm());
        }
        if (spec.dueOn() != null) {
            return spec.dueOn();
        }
        throw new ValidationException(
                FinanceErrorCode.INVALID_PAYMENT_PLAN, "Each installment needs a weekOfTerm or an explicit dueOn");
    }

    private static void validatePercents(List<ReplacePaymentPlanRequest.InstallmentRequest> specs) {
        if (specs.isEmpty()) {
            throw new ValidationException(
                    FinanceErrorCode.INVALID_PAYMENT_PLAN, "A payment plan needs at least one installment");
        }
        int previous = 0;
        for (var spec : specs) {
            if (spec.cumulativePercent() <= previous) {
                throw new ValidationException(
                        FinanceErrorCode.INVALID_PAYMENT_PLAN,
                        "Installment percents must increase (got " + spec.cumulativePercent() + " after " + previous + ")");
            }
            previous = spec.cumulativePercent();
        }
        if (previous != 100) {
            throw new ValidationException(
                    FinanceErrorCode.INVALID_PAYMENT_PLAN, "The last installment must bring the plan to 100%");
        }
    }

    private static PaymentPlanResponse.InstallmentResponse toResponse(PaymentInstallment row) {
        return new PaymentPlanResponse.InstallmentResponse(
                row.getLabel(),
                row.getCumulativePercent(),
                row.getWeekOfTerm(),
                row.getDueOn(),
                row.isPlacesHold(),
                row.isBlocksExams());
    }

    private record LedgerTotals(BigDecimal charges, BigDecimal paid) {}
}
