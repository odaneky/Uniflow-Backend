package com.university.lms.request.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * D3 (partial): a grade appeal is scoped to the specific grade it contests, not to the request
 * type as a whole — a student appealing grades in two different courses is two independent
 * requests. {@code uk_service_requests_open_appeal_per_grade} (V78) replaces the old
 * type-wide constraint for APPEAL specifically; every other type is unaffected.
 */
class ServiceRequestRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ServiceRequestRepository requestRepository;

    @Autowired
    private OwnerScopingFixtures fixtures;

    @Test
    @DisplayName("a student can have two open appeals open at once, for different grades")
    void twoOpenAppealsForDifferentGradesAreAllowed() {
        UUID studentId = fixtures.student().studentId();

        requestRepository.save(appeal(studentId, reference(), UUID.randomUUID()));
        requestRepository.saveAndFlush(appeal(studentId, reference(), UUID.randomUUID()));

        assertThat(requestRepository.findByStudentIdOrderByUpdatedAtDesc(studentId)).hasSize(2);
    }

    @Test
    @DisplayName("a second open appeal for the same grade is refused")
    void twoOpenAppealsForTheSameGradeAreRefused() {
        UUID studentId = fixtures.student().studentId();
        UUID gradeId = UUID.randomUUID();

        requestRepository.saveAndFlush(appeal(studentId, reference(), gradeId));

        assertThat(requestRepository.existsOpenAppealForGrade(gradeId.toString())).isTrue();
        assertThatThrownBy(() -> requestRepository.saveAndFlush(appeal(studentId, reference(), gradeId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsOpenAppealForGrade is false for a grade nobody has appealed")
    void noOpenAppealForAnUnrelatedGrade() {
        assertThat(requestRepository.existsOpenAppealForGrade(UUID.randomUUID().toString())).isFalse();
    }

    private static String reference() {
        return "GA" + Math.abs(UUID.randomUUID().hashCode() % 1_000_000_000);
    }

    private static ServiceRequest appeal(UUID studentId, String reference, UUID gradeId) {
        return new ServiceRequest(
                studentId,
                ServiceRequestType.APPEAL,
                reference,
                null,
                "{\"gradeId\":\"" + gradeId + "\"}",
                null);
    }
}
