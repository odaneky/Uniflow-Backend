package com.university.lms.financialaid.service;

import com.university.lms.financialaid.api.RegistrationHolds;
import com.university.lms.financialaid.domain.HoldType;
import com.university.lms.financialaid.domain.SapEvaluation;
import com.university.lms.financialaid.domain.ServiceHold;
import com.university.lms.financialaid.repository.SapEvaluationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates service holds and SAP outcomes for enrolment blocking checks. */
@Service
@Transactional(readOnly = true)
public class RegistrationHoldService implements RegistrationHolds {

    private final ServiceHoldService serviceHoldService;
    private final SapEvaluationRepository sapEvaluationRepository;

    public RegistrationHoldService(ServiceHoldService serviceHoldService, SapEvaluationRepository sapEvaluationRepository) {
        this.serviceHoldService = serviceHoldService;
        this.sapEvaluationRepository = sapEvaluationRepository;
    }

    @Override
    public List<HoldDetail> activeRegistrationHolds(UUID studentId) {
        List<HoldDetail> holds = new ArrayList<>();
        for (ServiceHold hold : serviceHoldService.activeEntitiesFor(studentId)) {
            holds.add(new HoldDetail(hold.getHoldType().name(), hold.getReason()));
        }
        sapEvaluationRepository.findByStudentIdOrderByEvaluatedAtDesc(studentId).stream()
                .filter(evaluation -> !evaluation.isMeetsSap())
                .max(Comparator.comparing(SapEvaluation::getEvaluatedAt))
                .ifPresent(evaluation -> holds.add(new HoldDetail(
                        HoldType.SAP.name(),
                        "Satisfactory Academic Progress requirements were not met for term "
                                + evaluation.getAcademicTermId())));
        return holds;
    }

    @Override
    public boolean blocksRegistration(UUID studentId) {
        return !activeRegistrationHolds(studentId).isEmpty();
    }
}
