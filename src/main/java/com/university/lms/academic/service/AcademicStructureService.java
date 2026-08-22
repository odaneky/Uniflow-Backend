package com.university.lms.academic.service;

import com.university.lms.academic.domain.AcademicErrorCode;
import com.university.lms.academic.domain.Department;
import com.university.lms.academic.domain.Faculty;
import com.university.lms.academic.domain.Programme;
import com.university.lms.academic.dto.CreateDepartmentRequest;
import com.university.lms.academic.dto.CreateFacultyRequest;
import com.university.lms.academic.dto.CreateProgrammeRequest;
import com.university.lms.academic.dto.DepartmentResponse;
import com.university.lms.academic.dto.FacultyResponse;
import com.university.lms.academic.dto.ProgrammeResponse;
import com.university.lms.academic.dto.ReplaceProgrammeCreditLoadRequest;
import com.university.lms.academic.dto.UpdateProgrammeRequest;
import com.university.lms.academic.repository.DepartmentRepository;
import com.university.lms.academic.repository.FacultyRepository;
import com.university.lms.academic.repository.ProgrammeRepository;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.identity.api.UserDirectory;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the Faculty → Department → Programme hierarchy.
 *
 * <p>Separate from {@code AcademicCalendarService} because the two change for different reasons:
 * the organisational structure is edited rarely and by administrators, while the calendar is
 * managed every term by the registry.
 *
 * <p>Codes are normalised to upper case on the way in. Without that, {@code comp} and {@code COMP}
 * would both satisfy the unique index and become two departments that every human reader would
 * consider the same one.
 */
@Service
@Transactional(readOnly = true)
public class AcademicStructureService {

    private static final Logger log = LoggerFactory.getLogger(AcademicStructureService.class);

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgrammeRepository programmeRepository;
    private final UserDirectory userDirectory;
    private final AcademicPolicyService academicPolicyService;

    public AcademicStructureService(
            FacultyRepository facultyRepository,
            DepartmentRepository departmentRepository,
            ProgrammeRepository programmeRepository,
            UserDirectory userDirectory,
            AcademicPolicyService academicPolicyService) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.programmeRepository = programmeRepository;
        this.userDirectory = userDirectory;
        this.academicPolicyService = academicPolicyService;
    }

    // ------------------------------------------------------------------
    // Faculties
    // ------------------------------------------------------------------

    @Transactional
    public FacultyResponse createFaculty(CreateFacultyRequest request) {
        String code = normalise(request.code());
        if (facultyRepository.existsByCode(code)) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.FACULTY_CODE_ALREADY_EXISTS, "Faculty code " + code + " is already in use");
        }
        requireUserIfPresent(request.deanUserId(), AcademicErrorCode.DEAN_NOT_FOUND, "dean");

        Faculty faculty = new Faculty(code, request.name());
        if (request.deanUserId() != null) {
            faculty.assignDean(request.deanUserId());
        }
        try {
            Faculty saved = facultyRepository.saveAndFlush(faculty);
            log.info("Created faculty {} ({})", saved.getCode(), saved.getId());
            return FacultyResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.FACULTY_CODE_ALREADY_EXISTS,
                    "Faculty code " + code + " is already in use",
                    ex);
        }
    }

    public PageResponse<FacultyResponse> findFaculties(Pageable pageable) {
        return PageResponse.from(facultyRepository.findAll(pageable), FacultyResponse::from);
    }

    public FacultyResponse findFaculty(UUID facultyId) {
        return FacultyResponse.from(requireFaculty(facultyId));
    }

    // ------------------------------------------------------------------
    // Departments
    // ------------------------------------------------------------------

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        Faculty faculty = requireFaculty(request.facultyId());
        String code = normalise(request.code());

        if (departmentRepository.existsByCode(code)) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.DEPARTMENT_CODE_ALREADY_EXISTS,
                    "Department code " + code + " is already in use");
        }
        requireUserIfPresent(request.headUserId(), AcademicErrorCode.DEPARTMENT_HEAD_NOT_FOUND, "department head");

        Department department = new Department(faculty, code, request.name());
        if (request.headUserId() != null) {
            department.assignHead(request.headUserId());
        }
        try {
            Department saved = departmentRepository.saveAndFlush(department);
            log.info("Created department {} ({})", saved.getCode(), saved.getId());
            return DepartmentResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.DEPARTMENT_CODE_ALREADY_EXISTS,
                    "Department code " + code + " is already in use",
                    ex);
        }
    }

    public PageResponse<DepartmentResponse> findDepartments(UUID facultyId, Pageable pageable) {
        if (facultyId != null) {
            requireFaculty(facultyId);
            return PageResponse.from(
                    departmentRepository.findByFacultyId(facultyId, pageable), DepartmentResponse::from);
        }
        return PageResponse.from(departmentRepository.findAll(pageable), DepartmentResponse::from);
    }

    public DepartmentResponse findDepartment(UUID departmentId) {
        return DepartmentResponse.from(requireDepartment(departmentId));
    }

    // ------------------------------------------------------------------
    // Programmes
    // ------------------------------------------------------------------

    @Transactional
    public ProgrammeResponse createProgramme(CreateProgrammeRequest request) {
        Department department = requireDepartment(request.departmentId());
        String code = normalise(request.code());

        if (programmeRepository.existsByCode(code)) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.PROGRAMME_CODE_ALREADY_EXISTS,
                    "Programme code " + code + " is already in use");
        }

        Programme programme = new Programme(
                department,
                code,
                request.name(),
                request.degreeAward(),
                request.totalCredits(),
                request.durationYears());
        try {
            Programme saved = programmeRepository.saveAndFlush(programme);
            log.info("Created programme {} ({})", saved.getCode(), saved.getId());
            return academicPolicyService.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    AcademicErrorCode.PROGRAMME_CODE_ALREADY_EXISTS,
                    "Programme code " + code + " is already in use",
                    ex);
        }
    }

    public PageResponse<ProgrammeResponse> findProgrammes(UUID departmentId, Pageable pageable) {
        if (departmentId != null) {
            requireDepartment(departmentId);
            return PageResponse.from(
                    programmeRepository.findByDepartmentId(departmentId, pageable), academicPolicyService::toResponse);
        }
        return PageResponse.from(programmeRepository.findAll(pageable), academicPolicyService::toResponse);
    }

    public ProgrammeResponse findProgramme(UUID programmeId) {
        return academicPolicyService.toResponse(requireProgramme(programmeId));
    }

    @Transactional
    public ProgrammeResponse updateProgramme(UUID programmeId, UpdateProgrammeRequest request) {
        Programme programme = requireProgramme(programmeId);
        programme.revise(
                request.name() == null ? programme.getName() : request.name().trim(),
                request.degreeAward() == null ? programme.getDegreeAward() : request.degreeAward().trim(),
                request.totalCredits() == null ? programme.getTotalCredits() : request.totalCredits(),
                request.durationYears() == null ? programme.getDurationYears() : request.durationYears());
        log.info("Updated programme {} ({})", programme.getCode(), programme.getId());
        return academicPolicyService.toResponse(programme);
    }

    @Transactional
    public ProgrammeResponse replaceCreditLoad(UUID programmeId, ReplaceProgrammeCreditLoadRequest request) {
        return academicPolicyService.replaceProgrammeLoad(programmeId, request);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Faculty requireFaculty(UUID id) {
        return facultyRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AcademicErrorCode.FACULTY_NOT_FOUND, "No faculty exists with id " + id));
    }

    private Department requireDepartment(UUID id) {
        return departmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AcademicErrorCode.DEPARTMENT_NOT_FOUND, "No department exists with id " + id));
    }

    private Programme requireProgramme(UUID id) {
        return programmeRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AcademicErrorCode.PROGRAMME_NOT_FOUND, "No programme exists with id " + id));
    }

    /** Cross-module check, made through identity's published contract rather than its tables. */
    private void requireUserIfPresent(UUID userId, AcademicErrorCode errorCode, String role) {
        if (userId != null && !userDirectory.exists(userId)) {
            throw new ResourceNotFoundException(errorCode, "No user exists with id " + userId + " to act as " + role);
        }
    }

    private static String normalise(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
