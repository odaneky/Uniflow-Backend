package com.university.lms.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.academic.domain.Department;
import com.university.lms.academic.domain.Faculty;
import com.university.lms.academic.dto.CreateDepartmentRequest;
import com.university.lms.academic.dto.CreateFacultyRequest;
import com.university.lms.academic.repository.DepartmentRepository;
import com.university.lms.academic.repository.FacultyRepository;
import com.university.lms.academic.repository.ProgrammeRepository;
import com.university.lms.identity.api.UserDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A5 groundwork: proves a faculty or department is mirrored as an {@code OrgUnit} the moment it is
 * created, and that the reconcile pass republishes for every existing one too.
 */
@ExtendWith(MockitoExtension.class)
class AcademicOrgUnitProvisioningTest {

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ProgrammeRepository programmeRepository;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private AcademicPolicyService academicPolicyService;

    @Mock
    private AcademicOutboxPublisher academicOutboxPublisher;

    @InjectMocks
    private AcademicStructureService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(facultyRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Faculty.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(departmentRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("creating a faculty publishes an org-unit-needed event for it")
    void creatingAFacultyPublishesAnEvent() {
        service.createFaculty(new CreateFacultyRequest("SCI", "Science", null));

        verify(academicOutboxPublisher).publishOrgUnitNeeded(eq("FACULTY"), any(), eq("SCI"), eq("Science"));
    }

    @Test
    @DisplayName("creating a department publishes an org-unit-needed event for it")
    void creatingADepartmentPublishesAnEvent() {
        UUID facultyId = UUID.randomUUID();
        Faculty faculty = new Faculty("SCI", "Science");
        org.springframework.test.util.ReflectionTestUtils.setField(faculty, "id", facultyId);
        when(facultyRepository.findById(facultyId)).thenReturn(Optional.of(faculty));

        service.createDepartment(new CreateDepartmentRequest(facultyId, "CS", "Computer Science", null));

        verify(academicOutboxPublisher).publishOrgUnitNeeded(eq("DEPARTMENT"), any(), eq("CS"), eq("Computer Science"));
    }

    @Test
    @DisplayName("reconcileOrgUnits republishes for every existing faculty and department")
    void reconcileRepublishesForEveryExistingUnit() {
        Faculty faculty = new Faculty("SCI", "Science");
        Department department = new Department(faculty, "CS", "Computer Science");
        when(facultyRepository.findAll()).thenReturn(List.of(faculty));
        when(departmentRepository.findAll()).thenReturn(List.of(department));

        int published = service.reconcileOrgUnits();

        assertThat(published).isEqualTo(2);
        verify(academicOutboxPublisher, times(1)).publishOrgUnitNeeded(eq("FACULTY"), any(), eq("SCI"), eq("Science"));
        verify(academicOutboxPublisher, times(1))
                .publishOrgUnitNeeded(eq("DEPARTMENT"), any(), eq("CS"), eq("Computer Science"));
    }
}
