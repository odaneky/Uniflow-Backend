package com.university.lms.financialaid.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.exception.ValidationException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryType;
import com.university.lms.finance.domain.StudentAccount;
import com.university.lms.finance.repository.AccountEntryRepository;
import com.university.lms.finance.repository.StudentAccountRepository;
import com.university.lms.financialaid.domain.AwardStatus;
import com.university.lms.financialaid.domain.AwardType;
import com.university.lms.financialaid.domain.FinancialAidAward;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.domain.IsirSnapshot;
import com.university.lms.financialaid.dto.FinancialAidAwardResponse;
import com.university.lms.financialaid.dto.IsirImportResponse;
import com.university.lms.financialaid.dto.IsirSnapshotResponse;
import com.university.lms.financialaid.dto.PackageAwardsRequest;
import com.university.lms.financialaid.repository.FinancialAidAwardRepository;
import com.university.lms.financialaid.repository.IsirSnapshotRepository;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinancialAidService {

    private static final BigDecimal DEFAULT_PELL_CAP = new BigDecimal("7395.00");

    private final IsirSnapshotRepository isirRepository;
    private final FinancialAidAwardRepository awardRepository;
    private final StudentAccountRepository accountRepository;
    private final AccountEntryRepository entryRepository;
    private final StudentDirectory studentDirectory;
    private final AcademicStructure academicStructure;
    private final CurrentUserProvider currentUserProvider;
    private final StaffAppointments staffAppointments;

    public FinancialAidService(
            IsirSnapshotRepository isirRepository,
            FinancialAidAwardRepository awardRepository,
            StudentAccountRepository accountRepository,
            AccountEntryRepository entryRepository,
            StudentDirectory studentDirectory,
            AcademicStructure academicStructure,
            CurrentUserProvider currentUserProvider,
            StaffAppointments staffAppointments) {
        this.isirRepository = isirRepository;
        this.awardRepository = awardRepository;
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.studentDirectory = studentDirectory;
        this.academicStructure = academicStructure;
        this.currentUserProvider = currentUserProvider;
        this.staffAppointments = staffAppointments;
    }

    public List<FinancialAidAwardResponse> awardsForStudent(UUID studentId) {
        requireStudentExists(studentId);
        requireOwnStudentOrStaff(studentId);
        return awardRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(FinancialAidAwardResponse::from)
                .toList();
    }

    public List<FinancialAidAwardResponse> ownAwards() {
        UUID studentId = ownStudentId();
        return awardsForStudent(studentId);
    }

    @Transactional
    public IsirImportResponse importIsir(String csv) {
        requireRegistry();
        if (csv == null || csv.isBlank()) {
            throw new ValidationException(FinancialAidErrorCode.ISIR_INVALID_ROW, "CSV payload is empty");
        }
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        List<IsirSnapshotResponse> snapshots = new ArrayList<>();
        Instant now = Instant.now();
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ValidationException(FinancialAidErrorCode.ISIR_INVALID_ROW, "CSV payload is empty");
            }
            Map<String, Integer> columns = parseHeader(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    skipped++;
                    continue;
                }
                String[] values = line.split(",", -1);
                UUID studentId = parseUuid(valueAt(values, columns, "student_id"), "student_id");
                if (!studentDirectory.exists(studentId)) {
                    throw new ResourceNotFoundException(
                            FinancialAidErrorCode.ISIR_STUDENT_NOT_FOUND,
                            "No student exists with id " + studentId);
                }
                String aidYear = valueAt(values, columns, "aid_year");
                BigDecimal efc = parseDecimal(valueAt(values, columns, "efc"));
                boolean pellEligible = parseBoolean(valueAt(values, columns, "pell_eligible"));
                String rawJson = valueAt(values, columns, "raw_json");
                var existing = isirRepository.findByStudentIdAndAidYear(studentId, aidYear);
                IsirSnapshot snapshot;
                if (existing.isPresent()) {
                    snapshot = existing.get();
                    snapshot.replace(efc, pellEligible, rawJson, now);
                    updated++;
                } else {
                    snapshot = new IsirSnapshot(studentId, aidYear, efc, pellEligible, rawJson, now);
                    imported++;
                }
                snapshots.add(IsirSnapshotResponse.from(isirRepository.save(snapshot)));
            }
        } catch (IOException ex) {
            throw new ValidationException(FinancialAidErrorCode.ISIR_INVALID_ROW, "Unable to read CSV payload");
        }
        return new IsirImportResponse(imported, updated, skipped, snapshots);
    }

    @Transactional
    public List<FinancialAidAwardResponse> packageAwards(UUID studentId, PackageAwardsRequest request) {
        requireRegistry();
        requireStudentExists(studentId);
        requireTerm(request.academicTermId());
        String aidYear = request.aidYear() == null ? currentAidYear() : request.aidYear();
        IsirSnapshot isir = isirRepository
                .findByStudentIdAndAidYear(studentId, aidYear)
                .orElseThrow(() -> new BusinessException(
                        FinancialAidErrorCode.ISIR_INVALID_ROW,
                        "No ISIR snapshot exists for student " + studentId + " aid year " + aidYear));

        List<FinancialAidAward> awards = new ArrayList<>();
        if (isir.isPellEligible()) {
            BigDecimal pellAmount = request.pellAmount() == null ? DEFAULT_PELL_CAP : request.pellAmount();
            awards.add(packageOne(studentId, request.academicTermId(), AwardType.PELL, pellAmount));
        }
        if (request.institutionalAmount() != null) {
            awards.add(packageOne(
                    studentId, request.academicTermId(), AwardType.INSTITUTIONAL, request.institutionalAmount()));
        }
        return awards.stream().map(FinancialAidAwardResponse::from).toList();
    }

    /**
     * At most one award per student, per term, per type. A retried packaging call — a client
     * timeout, a double submit — must not create a second award for the same type; it returns the
     * one already on offer instead. {@code uk_financial_aid_awards_student_term_type} is the actual
     * guarantee against a concurrent double-create; this check is what keeps the common,
     * non-concurrent retry from tripping it in the first place.
     */
    private FinancialAidAward packageOne(UUID studentId, UUID academicTermId, AwardType type, BigDecimal amount) {
        return awardRepository
                .findByStudentIdAndAcademicTermIdAndAwardType(studentId, academicTermId, type)
                .orElseGet(() -> awardRepository.save(
                        new FinancialAidAward(studentId, academicTermId, type, amount, AwardStatus.OFFERED)));
    }

    @Transactional
    public FinancialAidAwardResponse acceptOwnAward(UUID awardId) {
        UUID studentId = ownStudentId();
        return acceptAward(studentId, awardId);
    }

    @Transactional
    public FinancialAidAwardResponse acceptAward(UUID studentId, UUID awardId) {
        FinancialAidAward award = requireAward(awardId);
        if (!award.getStudentId().equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        requireOwnStudentOrStaff(studentId);
        try {
            award.accept();
        } catch (IllegalStateException ex) {
            throw new BusinessException(FinancialAidErrorCode.AWARD_INVALID_STATE, ex.getMessage());
        }
        return FinancialAidAwardResponse.from(award);
    }

    @Transactional
    public FinancialAidAwardResponse disburse(UUID awardId) {
        requireRegistry();
        FinancialAidAward award = requireAward(awardId);
        if (award.getStatus() == AwardStatus.DISBURSED) {
            throw new BusinessException(FinancialAidErrorCode.AWARD_ALREADY_DISBURSED, "Award is already disbursed");
        }
        Instant at = Instant.now();
        try {
            award.markDisbursed(at);
        } catch (IllegalStateException ex) {
            throw new BusinessException(FinancialAidErrorCode.AWARD_INVALID_STATE, ex.getMessage());
        }
        postDisbursementCredit(award, at);
        return FinancialAidAwardResponse.from(award);
    }

    private void postDisbursementCredit(FinancialAidAward award, Instant at) {
        StudentAccount account = accountRepository
                .lockByStudentId(award.getStudentId())
                .orElseGet(() -> {
                    try {
                        return accountRepository.saveAndFlush(new StudentAccount(award.getStudentId(), "USD"));
                    } catch (DataIntegrityViolationException ex) {
                        return accountRepository
                                .lockByStudentId(award.getStudentId())
                                .orElseThrow(() -> ex);
                    }
                });
        String reference = disbursementReference(award.getId());
        if (entryRepository.existsByAccountIdAndReference(account.getId(), reference)) {
            return;
        }
        entryRepository.save(new AccountEntry(
                account,
                AccountEntryType.CREDIT,
                award.getAmount().negate(),
                "Financial aid disbursement — " + award.getAwardType().name(),
                at,
                reference));
    }

    static String disbursementReference(UUID awardId) {
        return "fa-disburse:" + awardId;
    }

    private FinancialAidAward requireAward(UUID awardId) {
        return awardRepository
                .findById(awardId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.AWARD_NOT_FOUND, "No financial aid award exists with id " + awardId));
    }

    private void requireStudentExists(UUID studentId) {
        if (!studentDirectory.exists(studentId)) {
            throw new ResourceNotFoundException(
                    FinancialAidErrorCode.STUDENT_NOT_FOUND, "No student exists with id " + studentId);
        }
    }

    private void requireTerm(UUID academicTermId) {
        academicStructure
                .findTerm(academicTermId, Instant.now())
                .orElseThrow(() -> new ResourceNotFoundException(
                        FinancialAidErrorCode.STUDENT_NOT_FOUND,
                        "No academic term exists with id " + academicTermId));
    }

    private UUID ownStudentId() {
        CurrentUser caller = currentUserProvider.require();
        return studentDirectory
                .studentIdOfUser(caller.userId())
                .orElseThrow(() -> new ForbiddenException(
                        CommonErrorCode.ACCESS_DENIED, "You do not have a student record"));
    }

    private void requireOwnStudentOrStaff(UUID studentId) {
        CurrentUser caller = currentUserProvider.require();
        if (caller.isStaff()) {
            requireAuthorizedStaff(caller, studentId);
            return;
        }
        if (!ownStudentId().equals(studentId)) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    /**
     * A5: any staff role could view or accept an award on behalf of any student's financial aid
     * record, regardless of department — Title IV data, among the most sensitive in the system.
     * Same fail-open safety property and student -> programme -> department resolution as {@code
     * StudentService.requireSelfOrAuthorizedStaff}. Assumes the caller is already confirmed staff;
     * this only refines who among staff, not whether the caller is staff at all.
     */
    private void requireAuthorizedStaff(CurrentUser caller, UUID studentId) {
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return;
        }
        Optional<UUID> orgUnitId = studentDirectory
                .findById(studentId)
                .map(StudentDirectory.StudentSummary::programmeId)
                .flatMap(academicStructure::departmentOfProgramme)
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        if (orgUnitId.isPresent() && !staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get())) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    private void requireRegistry() {
        CurrentUser caller = currentUserProvider.require();
        if (!(caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR))) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
    }

    private static String currentAidYear() {
        int year = Instant.now().atZone(java.time.ZoneOffset.UTC).getYear();
        return year + "-" + (year + 1);
    }

    private static Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        String[] headers = headerLine.split(",", -1);
        for (int i = 0; i < headers.length; i++) {
            columns.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
        }
        if (!columns.containsKey("student_id") || !columns.containsKey("aid_year")) {
            throw new ValidationException(
                    FinancialAidErrorCode.ISIR_INVALID_ROW, "CSV header must include student_id and aid_year");
        }
        return columns;
    }

    private static String valueAt(String[] values, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= values.length) {
            return null;
        }
        String value = values[index].trim();
        return value.isEmpty() ? null : value;
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null) {
            throw new ValidationException(FinancialAidErrorCode.ISIR_INVALID_ROW, field + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(FinancialAidErrorCode.ISIR_INVALID_ROW, field + " must be a UUID");
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new ValidationException(FinancialAidErrorCode.ISIR_INVALID_ROW, "efc must be numeric");
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw new ValidationException(
                    FinancialAidErrorCode.ISIR_INVALID_ROW, "pell_eligible must be true or false");
        };
    }
}
