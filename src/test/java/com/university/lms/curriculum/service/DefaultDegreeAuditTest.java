package com.university.lms.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.curriculum.api.DegreeAudit;
import com.university.lms.curriculum.domain.GraduationClearanceItem;
import com.university.lms.curriculum.dto.DegreeProgressResponse;
import com.university.lms.curriculum.repository.GraduationClearanceItemRepository;
import com.university.lms.student.api.ResidencyClassification;
import com.university.lms.student.api.StudentDirectory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultDegreeAuditTest {

    @Mock
    private CurriculumService curriculumService;

    @Mock
    private GraduationClearanceItemRepository clearanceItemRepository;

    @Mock
    private AcademicStructure academicStructure;

    @Mock
    private StudentDirectory studentDirectory;

    @InjectMocks
    private DefaultDegreeAudit degreeAudit;

    private static DegreeProgressResponse fullyMetProgress(UUID programmeId) {
        return new DegreeProgressResponse(
                programmeId, "BSC-CS", "Computer Science", "BSc", 120, 120, 120, new BigDecimal("3.50"), List.of(), List.of());
    }

    @Test
    @DisplayName("a pending clearance item blocks graduation even when everything else is met")
    void pendingClearanceItemBlocksGraduation() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();

        when(curriculumService.progressOf(studentId)).thenReturn(fullyMetProgress(programmeId));
        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "20260001", programmeId, true, ResidencyClassification.IN_DISTRICT)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-CS", "Computer Science", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        when(clearanceItemRepository.findByStudentIdOrderByItemTypeAsc(studentId))
                .thenReturn(List.of(new GraduationClearanceItem(studentId, "LIBRARY_FINES")));
        when(curriculumService.residencyCreditsFor(programmeId)).thenReturn(Optional.empty());

        DegreeAudit.Eligibility eligibility = degreeAudit.eligibility(studentId);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.blockers()).anyMatch(b -> b.contains("LIBRARY_FINES"));
    }

    @Test
    @DisplayName("a cleared item does not block graduation")
    void clearedItemDoesNotBlock() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();

        when(curriculumService.progressOf(studentId)).thenReturn(fullyMetProgress(programmeId));
        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "20260001", programmeId, true, ResidencyClassification.IN_DISTRICT)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-CS", "Computer Science", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        GraduationClearanceItem cleared = new GraduationClearanceItem(studentId, "LIBRARY_FINES");
        cleared.clear(UUID.randomUUID(), "Paid in full");
        when(clearanceItemRepository.findByStudentIdOrderByItemTypeAsc(studentId)).thenReturn(List.of(cleared));
        when(curriculumService.residencyCreditsFor(programmeId)).thenReturn(Optional.empty());

        DegreeAudit.Eligibility eligibility = degreeAudit.eligibility(studentId);

        assertThat(eligibility.eligible()).isTrue();
    }

    @Test
    @DisplayName("falling short of the residency requirement blocks graduation")
    void residencyShortfallBlocksGraduation() {
        UUID studentId = UUID.randomUUID();
        UUID programmeId = UUID.randomUUID();

        when(curriculumService.progressOf(studentId)).thenReturn(fullyMetProgress(programmeId));
        when(studentDirectory.findById(studentId))
                .thenReturn(Optional.of(new StudentDirectory.StudentSummary(
                        studentId, UUID.randomUUID(), "20260001", programmeId, true, ResidencyClassification.IN_DISTRICT)));
        when(academicStructure.findProgramme(programmeId))
                .thenReturn(Optional.of(new AcademicStructure.ProgrammeSummary(
                        programmeId, "BSC-CS", "Computer Science", "BSc", 120, true, "DEGREE", new BigDecimal("2.00"))));
        when(clearanceItemRepository.findByStudentIdOrderByItemTypeAsc(studentId)).thenReturn(List.of());
        // fullyMetProgress reports 120 creditsEarned; require more in-residence than that.
        when(curriculumService.residencyCreditsFor(programmeId)).thenReturn(Optional.of(130));

        DegreeAudit.Eligibility eligibility = degreeAudit.eligibility(studentId);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.blockers()).anyMatch(b -> b.contains("Residency"));
    }
}
