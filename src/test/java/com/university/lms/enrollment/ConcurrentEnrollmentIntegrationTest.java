package com.university.lms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.common.exception.ApplicationException;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.enrollment.domain.EnrollmentErrorCode;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import com.university.lms.enrollment.dto.CreateEnrollmentRequest;
import com.university.lms.enrollment.dto.EnrollmentResponse;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.enrollment.service.EnrollmentService;
import com.university.lms.student.domain.Student;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.support.RunAs;
import com.university.lms.support.AcademicFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * The tests that justify the concurrency design.
 *
 * <p>These deliberately run against real PostgreSQL rather than an in-memory database, because the
 * properties under test are database properties: whether a unique index actually rejects the
 * second writer, and whether a guarded {@code UPDATE} really serialises two callers competing for
 * the last seat. An emulated engine can pass these tests while the real one fails.
 *
 * <p>Every thread is released from a single latch so the requests genuinely overlap rather than
 * arriving in a comfortable sequence.
 */
@Import(AcademicFixtures.class)
class ConcurrentEnrollmentIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AcademicFixtures fixtures;

    @Test
    @DisplayName("a section with N seats admits exactly N students, however many apply at once")
    void concurrentEnrolmentNeverOverfillsASection() throws Exception {
        int capacity = 10;
        int applicants = 40;

        AcademicTerm term = fixtures.openTerm();
        CourseSection section = fixtures.openSection(term, capacity);

        List<Student> students = new ArrayList<>();
        for (int i = 0; i < applicants; i++) {
            students.add(fixtures.student(fixtures.programme()));
        }

        Outcome outcome = raceToEnrol(students, section.getId(), applicants);

        assertThat(outcome.succeeded()).as("exactly the available seats are filled — %s", outcome).isEqualTo(capacity);
        assertThat(outcome.waitlisted())
                .as("every other applicant is waitlisted — %s", outcome)
                .isEqualTo(applicants - capacity);
        assertThat(outcome.unexpectedFailures())
                .as("no request failed for an unanticipated reason")
                .isEmpty();

        // The counter and the seated rows must agree; waitlisted rows must not occupy capacity.
        assertThat(fixtures.reload(section).getEnrolledCount()).isEqualTo(capacity);
        assertThat(enrollmentRepository.countByCourseSectionIdAndStatusIn(
                        section.getId(), List.of(EnrollmentStatus.ENROLLED)))
                .isEqualTo(capacity);
        assertThat(enrollmentRepository.countByCourseSectionIdAndStatusIn(
                        section.getId(), List.of(EnrollmentStatus.WAITLISTED)))
                .isEqualTo(applicants - capacity);
    }

    @Test
    @DisplayName("the same student racing themselves is enrolled exactly once")
    void concurrentDuplicateEnrolmentIsRejectedByTheUniqueIndex() throws Exception {
        int attempts = 16;

        AcademicTerm term = fixtures.openTerm();
        CourseSection section = fixtures.openSection(term, 50);
        Student student = fixtures.student(fixtures.programme());

        Outcome outcome = raceToEnrol(List.of(student), section.getId(), attempts);

        assertThat(outcome.succeeded()).as("only one attempt may win — %s", outcome).isEqualTo(1);
        assertThat(outcome.rejectedAsDuplicate()).isEqualTo(attempts - 1);
        assertThat(outcome.unexpectedFailures()).isEmpty();

        // Crucially, the losing attempts must not have consumed capacity on their way out.
        assertThat(fixtures.reload(section).getEnrolledCount())
                .as("a rolled-back enrolment must return its seat")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /**
     * Fires {@code attempts} enrolment requests simultaneously. When fewer students than attempts
     * are supplied, the same student is used repeatedly — which is how the duplicate race is set up.
     */
    private Outcome raceToEnrol(List<Student> students, UUID sectionId, int attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(attempts, 32));
        CountDownLatch startGun = new CountDownLatch(1);

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger waitlisted = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        List<String> unexpected = java.util.Collections.synchronizedList(new ArrayList<>());

        // Resolve the caller once, before the burst. Provisioning opens a second transaction, so
        // forty threads all meeting their own first request at once would each hold two connections
        // and exhaust the pool — a real constraint, but not the one this test is measuring. In
        // production a user exists long before they join a registration rush.
        RunAs.staff(() -> currentUserProvider.require());

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            UUID studentId = students.get(i % students.size()).getId();
            tasks.add(() -> {
                startGun.await(10, TimeUnit.SECONDS);
                try {
                    // The security context is a ThreadLocal and is not inherited by pool threads,
                    // so it has to be installed inside the task. Staff, because this test is about
                    // seat contention, not about who may enrol whom.
                    EnrollmentResponse created =
                            RunAs.staff(() -> enrollmentService.enrol(new CreateEnrollmentRequest(studentId, sectionId)));
                    if (created.status() == EnrollmentStatus.WAITLISTED) {
                        waitlisted.incrementAndGet();
                    } else {
                        succeeded.incrementAndGet();
                    }
                } catch (ApplicationException ex) {
                    if (ex.getErrorCode() == EnrollmentErrorCode.ENROLLMENT_ALREADY_EXISTS) {
                        duplicate.incrementAndGet();
                    } else {
                        unexpected.add(ex.getErrorCode().code() + ": " + ex.getMessage());
                    }
                } catch (Exception ex) {
                    // Keep the stack: an unexpected failure here is the interesting case, and a
                    // bare class name would not have been enough to find the last one.
                    java.io.StringWriter stack = new java.io.StringWriter();
                    ex.printStackTrace(new java.io.PrintWriter(stack));
                    unexpected.add(ex.getClass().getName() + ": " + ex.getMessage() + System.lineSeparator() + stack);
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        startGun.countDown();
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        return new Outcome(succeeded.get(), waitlisted.get(), duplicate.get(), List.copyOf(unexpected));
    }

    private record Outcome(
            int succeeded, int waitlisted, int rejectedAsDuplicate, List<String> unexpectedFailures) {

        /** Reported on every assertion so a failure shows the whole tally, not one number. */
        @Override
        public String toString() {
            return "succeeded=%d waitlisted=%d rejectedAsDuplicate=%d unexpected=%s"
                    .formatted(succeeded, waitlisted, rejectedAsDuplicate, unexpectedFailures);
        }
    }
}
