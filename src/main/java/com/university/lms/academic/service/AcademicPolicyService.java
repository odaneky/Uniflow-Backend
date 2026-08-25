package com.university.lms.academic.service;

import com.university.lms.academic.api.AcademicStructure.CreditLoad;
import com.university.lms.administration.api.Auditable;
import com.university.lms.administration.api.AuditTrail;
import com.university.lms.academic.domain.AcademicErrorCode;
import com.university.lms.academic.domain.CreditLoadPolicy;
import com.university.lms.academic.domain.InstitutionAcademicPolicy;
import com.university.lms.academic.domain.Programme;
import com.university.lms.academic.dto.AcademicPolicyResponse;
import com.university.lms.academic.dto.ProgrammeResponse;
import com.university.lms.academic.dto.ReplaceAcademicPolicyRequest;
import com.university.lms.academic.dto.ReplaceProgrammeCreditLoadRequest;
import com.university.lms.academic.repository.InstitutionAcademicPolicyRepository;
import com.university.lms.academic.repository.ProgrammeRepository;
import com.university.lms.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AcademicPolicyService {

    private final InstitutionAcademicPolicyRepository policyRepository;
    private final ProgrammeRepository programmeRepository;

    public AcademicPolicyService(
            InstitutionAcademicPolicyRepository policyRepository, ProgrammeRepository programmeRepository) {
        this.policyRepository = policyRepository;
        this.programmeRepository = programmeRepository;
    }

    public AcademicPolicyResponse institutionPolicy() {
        return AcademicPolicyResponse.from(requirePolicy());
    }

    public CreditLoad creditLoadFor(UUID programmeId) {
        InstitutionAcademicPolicy policy = requirePolicy();
        Programme programme = programmeId == null ? null : programmeRepository.findById(programmeId).orElse(null);
        return CreditLoadPolicy.resolve(
                policy.getMinSemesterCredits(),
                policy.getMaxSemesterCredits(),
                programme == null ? null : programme.getMinSemesterCredits(),
                programme == null ? null : programme.getMaxSemesterCredits());
    }

    public ProgrammeResponse toResponse(Programme programme) {
        return ProgrammeResponse.from(programme, creditLoadFor(programme.getId()));
    }

    @Auditable(
            action = AuditTrail.Action.ACADEMIC_POLICY_REPLACED,
            entityType = AuditTrail.EntityType.INSTITUTION,
            entityId = "null",
            details = "'min ' + #request.minSemesterCredits() + ', max ' + #request.maxSemesterCredits()")
    @Transactional
    public AcademicPolicyResponse replaceInstitution(ReplaceAcademicPolicyRequest request) {
        CreditLoadPolicy.resolve(request.minSemesterCredits(), request.maxSemesterCredits(), null, null);
        InstitutionAcademicPolicy policy = requirePolicy();
        int hours = request.checkoutCorrectionHours() == null
                ? policy.getCheckoutCorrectionHours()
                : request.checkoutCorrectionHours();
        policy.replace(request.minSemesterCredits(), request.maxSemesterCredits(), hours);
        return AcademicPolicyResponse.from(policy);
    }

    // The only external entry point is AcademicStructureService.replaceCreditLoad, a pure
    // delegate with no logic of its own — annotating both would double-fire, since (unlike a
    // same-class self-invocation, which the proxy never sees) that call reaches this method
    // through a genuinely separate injected bean and does pass through this proxy.
    @Auditable(
            action = AuditTrail.Action.PROGRAMME_CREDIT_LOAD_REPLACED,
            entityType = AuditTrail.EntityType.PROGRAMME,
            entityId = "#programmeId")
    @Transactional
    public ProgrammeResponse replaceProgrammeLoad(UUID programmeId, ReplaceProgrammeCreditLoadRequest request) {
        Programme programme = programmeRepository
                .findById(programmeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AcademicErrorCode.PROGRAMME_NOT_FOUND, "No programme exists with id " + programmeId));
        CreditLoad load = CreditLoadPolicy.resolve(
                requirePolicy().getMinSemesterCredits(),
                requirePolicy().getMaxSemesterCredits(),
                request.minSemesterCredits(),
                request.maxSemesterCredits());
        programme.replaceCreditLoad(request.minSemesterCredits(), request.maxSemesterCredits());
        return ProgrammeResponse.from(programme, load);
    }

    private InstitutionAcademicPolicy requirePolicy() {
        return policyRepository
                .findById(InstitutionAcademicPolicy.SINGLETON_ID)
                .orElseGet(() -> policyRepository.save(new InstitutionAcademicPolicy(
                        CreditLoadPolicy.DEFAULT_MIN, CreditLoadPolicy.DEFAULT_MAX)));
    }
}
