package com.university.lms.admissions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.admissions.domain.AdmissionsErrorCode;
import com.university.lms.admissions.domain.ApplicationFormFieldDefinition;
import com.university.lms.admissions.domain.ApplicationFormFieldType;
import com.university.lms.admissions.domain.ApplicationFormSection;
import com.university.lms.admissions.domain.ProgrammeApplicationForm;
import com.university.lms.admissions.dto.ApplicationFormFieldBody;
import com.university.lms.admissions.dto.ApplicationFormFieldResponse;
import com.university.lms.admissions.dto.ProgrammeApplicationFormResponse;
import com.university.lms.admissions.dto.ReplaceProgrammeApplicationFormRequest;
import com.university.lms.admissions.repository.ProgrammeApplicationFormRepository;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProgrammeApplicationFormService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]{0,59}$");
    private static final Set<String> RESERVED_KEYS = Set.of(
            "applicantname",
            "applicantemail",
            "programmeid",
            "academictermid",
            "reference",
            "status",
            "studentid");

    private final ProgrammeApplicationFormRepository formRepository;
    private final AcademicStructure academicStructure;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public ProgrammeApplicationFormService(
            ProgrammeApplicationFormRepository formRepository,
            AcademicStructure academicStructure,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper) {
        this.formRepository = formRepository;
        this.academicStructure = academicStructure;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    public ProgrammeApplicationFormResponse findByProgrammeId(UUID programmeId) {
        var programme = academicStructure
                .findProgramme(programmeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AdmissionsErrorCode.APPLICATION_PROGRAMME_NOT_FOUND,
                        "No programme exists with id " + programmeId));
        var stored = formRepository.findById(programmeId);
        List<ApplicationFormFieldDefinition> fields =
                stored.map(row -> parseFields(row.getFieldsJson())).orElseGet(this::defaultFields);
        return new ProgrammeApplicationFormResponse(
                programmeId,
                programme.code(),
                programme.name(),
                stored.isPresent(),
                fields.stream().map(ApplicationFormFieldResponse::from).toList());
    }

    @Transactional
    public ProgrammeApplicationFormResponse replace(UUID programmeId, ReplaceProgrammeApplicationFormRequest request) {
        requireStaffEditor();
        if (!academicStructure.programmeExists(programmeId)) {
            throw new ResourceNotFoundException(
                    AdmissionsErrorCode.APPLICATION_PROGRAMME_NOT_FOUND,
                    "No programme exists with id " + programmeId);
        }
        List<ApplicationFormFieldDefinition> normalized = normalizeFields(request.fields());
        String json = serializeFields(normalized);
        ProgrammeApplicationForm saved = formRepository
                .findById(programmeId)
                .map(existing -> {
                    existing.replaceFields(json);
                    return formRepository.save(existing);
                })
                .orElseGet(() -> formRepository.save(new ProgrammeApplicationForm(programmeId, json)));
        var programme = academicStructure.findProgramme(programmeId).orElseThrow();
        return new ProgrammeApplicationFormResponse(
                programmeId,
                programme.code(),
                programme.name(),
                true,
                normalized.stream().map(ApplicationFormFieldResponse::from).toList());
    }

    public Map<String, Object> validatePayload(UUID programmeId, Map<String, Object> payload) {
        List<ApplicationFormFieldDefinition> fields = resolveFields(programmeId);
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String text = String.valueOf(entry.getValue()).trim();
                    if (!text.isBlank()) {
                        normalized.put(entry.getKey(), text);
                    }
                }
            }
        }
        for (ApplicationFormFieldDefinition field : fields) {
            Object value = normalized.get(field.key());
            if (field.required() && (value == null || String.valueOf(value).isBlank())) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_FORM_FIELD_MISSING,
                        field.label() + " is required");
            }
            if (field.type() == ApplicationFormFieldType.SELECT && value != null) {
                boolean allowed = field.options().stream().anyMatch(option -> option.value().equals(String.valueOf(value)));
                if (!allowed) {
                    throw new BusinessException(
                            AdmissionsErrorCode.APPLICATION_FORM_INVALID,
                            field.label() + " has an invalid choice");
                }
            }
        }
        for (String key : normalized.keySet()) {
            if (fields.stream().noneMatch(field -> field.key().equals(key))) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_FORM_INVALID, "Unknown application field: " + key);
            }
        }
        return normalized;
    }

    public List<ApplicationFormFieldDefinition> resolveFields(UUID programmeId) {
        return formRepository.findById(programmeId).map(row -> parseFields(row.getFieldsJson())).orElseGet(this::defaultFields);
    }

    public List<ApplicationFormFieldDefinition> defaultFields() {
        return List.of(
                new ApplicationFormFieldDefinition(
                        "phone",
                        "Phone",
                        ApplicationFormFieldType.PHONE,
                        ApplicationFormSection.CONTACT,
                        false,
                        null,
                        null,
                        0,
                        List.of()),
                new ApplicationFormFieldDefinition(
                        "address",
                        "Mailing address",
                        ApplicationFormFieldType.TEXT,
                        ApplicationFormSection.DETAILS,
                        false,
                        null,
                        null,
                        0,
                        List.of()),
                new ApplicationFormFieldDefinition(
                        "previousSchool",
                        "Previous school",
                        ApplicationFormFieldType.TEXT,
                        ApplicationFormSection.DETAILS,
                        false,
                        null,
                        null,
                        1,
                        List.of()),
                new ApplicationFormFieldDefinition(
                        "personalStatement",
                        "Personal statement",
                        ApplicationFormFieldType.TEXTAREA,
                        ApplicationFormSection.DETAILS,
                        false,
                        null,
                        null,
                        2,
                        List.of()));
    }

    private List<ApplicationFormFieldDefinition> normalizeFields(List<ApplicationFormFieldBody> bodies) {
        if (bodies == null || bodies.isEmpty()) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_FORM_INVALID, "At least one field is required");
        }
        if (bodies.size() > 25) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_FORM_INVALID, "At most 25 fields are allowed");
        }
        Set<String> keys = new HashSet<>();
        List<ApplicationFormFieldDefinition> normalized = new ArrayList<>();
        int index = 0;
        for (ApplicationFormFieldBody body : bodies) {
            String key = body.key().trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_FORM_INVALID,
                        "Field key must start with a letter and use camelCase: " + key);
            }
            if (RESERVED_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_FORM_INVALID, "Field key is reserved: " + key);
            }
            if (!keys.add(key)) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_FORM_INVALID, "Duplicate field key: " + key);
            }
            if (body.type() == ApplicationFormFieldType.SELECT
                    && (body.options() == null || body.options().isEmpty())) {
                throw new BusinessException(
                        AdmissionsErrorCode.APPLICATION_FORM_INVALID,
                        "Select fields must include at least one option: " + key);
            }
            List<ApplicationFormFieldDefinition.ApplicationFormOptionDefinition> options =
                    body.options() == null
                            ? List.of()
                            : body.options().stream()
                                    .map(option -> new ApplicationFormFieldDefinition.ApplicationFormOptionDefinition(
                                            option.value().trim(), option.label().trim()))
                                    .toList();
            normalized.add(new ApplicationFormFieldDefinition(
                    key,
                    body.label().trim(),
                    body.type(),
                    body.section(),
                    Boolean.TRUE.equals(body.required()),
                    blankToNull(body.placeholder()),
                    blankToNull(body.helpText()),
                    body.sortOrder() == null ? index : body.sortOrder(),
                    options));
            index++;
        }
        normalized.sort(Comparator.comparingInt(ApplicationFormFieldDefinition::sortOrder));
        return List.copyOf(normalized);
    }

    private List<ApplicationFormFieldDefinition> parseFields(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_FORM_INVALID, "Stored application form is invalid", ex);
        }
    }

    private String serializeFields(List<ApplicationFormFieldDefinition> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    AdmissionsErrorCode.APPLICATION_FORM_INVALID, "Application form could not be saved", ex);
        }
    }

    private CurrentUser requireStaffEditor() {
        CurrentUser caller = currentUserProvider.require();
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN) || caller.hasRole(SecurityRoles.REGISTRAR)) {
            return caller;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to configure application forms");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
