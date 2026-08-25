package com.university.lms.academic;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.academic.domain.Programme;
import com.university.lms.academic.dto.CreateFacultyRequest;
import com.university.lms.academic.dto.FacultyResponse;
import com.university.lms.academic.dto.RegistrationWindowRequest;
import com.university.lms.academic.dto.ReplaceProgrammeCreditLoadRequest;
import com.university.lms.academic.service.AcademicCalendarService;
import com.university.lms.academic.service.AcademicStructureService;
import com.university.lms.administration.domain.AuditEvent;
import com.university.lms.administration.repository.AuditEventRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/**
 * B3: proves the real DI/AOP wiring for the academic module's {@code @Auditable} methods, and in
 * particular that {@code AcademicStructureService.replaceCreditLoad} — a pure delegate to
 * {@code AcademicPolicyService.replaceProgrammeLoad} through a genuinely separate injected bean,
 * not a same-class self-invocation — fires exactly once, not twice. Unlike a same-class
 * self-invocation, that call does pass through the callee's own proxy, so annotating both methods
 * would double the event; only {@code replaceProgrammeLoad} carries {@code @Auditable}, and this
 * is the only test in the suite that can actually observe whether that reasoning was correct
 * end to end, rather than just reading correctly in isolation.
 */
class AcademicAuditIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AcademicStructureService academicStructureService;

    @Autowired
    private AcademicCalendarService academicCalendarService;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    @DisplayName("creating a faculty writes exactly one audit event")
    void creatingAFacultyWritesAnAuditEvent() throws Exception {
        String code = "AUD" + System.nanoTime() % 100000;
        FacultyResponse created = RunAs.staff(
                () -> academicStructureService.createFaculty(new CreateFacultyRequest(code, "Audit Test Faculty", null)));

        List<AuditEvent> events = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Faculty", created.id(), Pageable.unpaged())
                .getContent();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo("FACULTY_CREATED");
    }

    @Test
    @DisplayName(
            "replaceCreditLoad fires the audit event exactly once, through AcademicPolicyService's "
                    + "own @Auditable, not twice from both layers")
    void replaceCreditLoadFiresExactlyOnce() throws Exception {
        Programme programme = academicFixtures.programme();

        RunAs.staff(() -> academicStructureService.replaceCreditLoad(
                programme.getId(), new ReplaceProgrammeCreditLoadRequest(12, 18)));

        List<AuditEvent> events = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Programme", programme.getId(), Pageable.unpaged())
                .getContent();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo("PROGRAMME_CREDIT_LOAD_REPLACED");
    }

    @Test
    @DisplayName("opening a term's registration window writes an audit event")
    void settingTheRegistrationWindowWritesAnAuditEvent() throws Exception {
        var term = academicFixtures.openTerm();
        Instant opensAt = Instant.now();
        Instant closesAt = opensAt.plusSeconds(3600);

        RunAs.staff(() -> academicCalendarService.setRegistrationWindow(
                term.getId(), new RegistrationWindowRequest(opensAt, closesAt)));

        List<AuditEvent> events = auditEventRepository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc("AcademicTerm", term.getId(), Pageable.unpaged())
                .getContent();
        assertThat(events).anyMatch(e -> "REGISTRATION_WINDOW_SET".equals(e.getAction()));
    }
}
