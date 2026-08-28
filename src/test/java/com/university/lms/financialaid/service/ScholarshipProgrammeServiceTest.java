package com.university.lms.financialaid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.financialaid.domain.FinancialAidErrorCode;
import com.university.lms.financialaid.domain.ScholarshipProgramme;
import com.university.lms.financialaid.dto.CreateScholarshipProgrammeRequest;
import com.university.lms.financialaid.dto.ScholarshipProgrammeResponse;
import com.university.lms.financialaid.dto.UpdateScholarshipProgrammeRequest;
import com.university.lms.financialaid.repository.ScholarshipProgrammeRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** E9: the scholarship-programme catalog, CRUD + name-uniqueness + the partial-update convention. */
@ExtendWith(MockitoExtension.class)
class ScholarshipProgrammeServiceTest {

    @Mock
    private ScholarshipProgrammeRepository repository;

    private ScholarshipProgrammeService service;

    @BeforeEach
    void setUp() {
        service = new ScholarshipProgrammeService(repository);
        lenient().when(repository.save(any(ScholarshipProgramme.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreateScholarshipProgrammeRequest createRequest(String name) {
        return new CreateScholarshipProgrammeRequest(
                name, "Acme Foundation", "Merit-based award", new BigDecimal("2500.00"), true, 3, "GPA >= 3.5");
    }

    @Test
    @DisplayName("creating a programme with a fresh name succeeds")
    void creatingWithAFreshNameSucceeds() {
        when(repository.existsByNameIgnoreCase("Acme Merit Scholarship")).thenReturn(false);

        ScholarshipProgrammeResponse response = service.create(createRequest("Acme Merit Scholarship"));

        assertThat(response.name()).isEqualTo("Acme Merit Scholarship");
        assertThat(response.sponsorName()).isEqualTo("Acme Foundation");
        assertThat(response.defaultAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.renewable()).isTrue();
        assertThat(response.maxRenewals()).isEqualTo(3);
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("creating a programme with a name already in use (case-insensitively) is refused")
    void creatingWithADuplicateNameIsRefused() {
        when(repository.existsByNameIgnoreCase("acme merit scholarship")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("acme merit scholarship")))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .satisfies(thrown -> assertThat(((ResourceAlreadyExistsException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_PROGRAMME_NAME_ALREADY_EXISTS));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a null field on update leaves the existing value unchanged")
    void updateWithNullFieldsPreservesExistingValues() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Acme Merit Scholarship", "Acme Foundation", "Merit-based award",
                new BigDecimal("2500.00"), true, 3, "GPA >= 3.5");
        when(repository.findById(programme.getId())).thenReturn(Optional.of(programme));

        ScholarshipProgrammeResponse response = service.update(
                programme.getId(),
                new UpdateScholarshipProgrammeRequest(
                        null, null, false, null, null, null, null, null, null, null));

        assertThat(response.name()).isEqualTo("Acme Merit Scholarship");
        assertThat(response.sponsorName()).isEqualTo("Acme Foundation");
        assertThat(response.defaultAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.maxRenewals()).isEqualTo(3);
    }

    @Test
    @DisplayName("clearSponsorName explicitly nulls the sponsor, distinct from leaving it unset")
    void clearSponsorNameNullsTheSponsor() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Acme Merit Scholarship", "Acme Foundation", null, new BigDecimal("2500.00"), false, null, null);
        when(repository.findById(programme.getId())).thenReturn(Optional.of(programme));

        ScholarshipProgrammeResponse response = service.update(
                programme.getId(),
                new UpdateScholarshipProgrammeRequest(
                        null, null, true, null, null, null, null, null, null, null));

        assertThat(response.sponsorName()).isNull();
    }

    @Test
    @DisplayName("deactivating flips active to false rather than deleting the row")
    void deactivatingFlipsActiveFalse() {
        ScholarshipProgramme programme = new ScholarshipProgramme(
                "Acme Merit Scholarship", null, null, new BigDecimal("2500.00"), false, null, null);
        when(repository.findById(programme.getId())).thenReturn(Optional.of(programme));

        service.deactivate(programme.getId());

        assertThat(programme.isActive()).isFalse();
    }

    @Test
    @DisplayName("looking up a programme that does not exist is a 404")
    void lookingUpAMissingProgrammeIs404() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(thrown -> assertThat(((ResourceNotFoundException) thrown).getErrorCode())
                        .isEqualTo(FinancialAidErrorCode.SCHOLARSHIP_PROGRAMME_NOT_FOUND));
    }
}
