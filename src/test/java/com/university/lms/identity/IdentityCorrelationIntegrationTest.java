package com.university.lms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.administration.domain.AuditEvent;
import com.university.lms.administration.repository.AuditEventRepository;
import com.university.lms.common.exception.ApplicationException;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.identity.service.UserProvisioningService;
import com.university.lms.student.domain.Student;
import com.university.lms.student.repository.StudentRepository;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * How a bearer token becomes a person.
 *
 * <p>This is the most security-sensitive logic in the application. Correlation decides which
 * academic record a login owns, so a mistake here does not produce an error — it silently hands one
 * student another student's transcript. Every branch is pinned, including the ones that must refuse.
 */
class IdentityCorrelationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserProvisioningService provisioningService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private com.university.lms.support.AcademicFixtures fixtures;

    private static Jwt token(String subject, String username, String email, boolean emailVerified, String studentNumber) {
        Jwt.Builder builder = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject(subject)
                .claim("preferred_username", username)
                .claim("email_verified", emailVerified);
        if (email != null) {
            builder.claim("email", email);
        }
        if (studentNumber != null) {
            builder.claim("student_number", studentNumber);
        }
        return builder.build();
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Nested
    @DisplayName("Correlation by institutional student number")
    class ByStudentNumber {

        /**
         * The flow that makes a real student login work: the registry creates the record in advance,
         * the identity provider asserts the same number, and the first login joins them.
         */
        @Test
        @DisplayName("links a login to the student record the registry created in advance")
        void linksToAPreExistingStudentRecord() {
            String tag = unique();
            String studentNumber = "20" + tag;
            User provisioned = userRepository.saveAndFlush(
                    new User("pre-" + tag, "pre-" + tag + "@university.test", "Pre", "Existing"));
            studentRepository.saveAndFlush(new Student(
                    provisioned.getId(), studentNumber, fixtures.programme().getId(), LocalDate.of(2025, 9, 1)));

            String subject = UUID.randomUUID().toString();
            User resolved = provisioningService.provision(
                    token(subject, studentNumber, "someone-else-" + tag + "@university.test", true, studentNumber));

            assertThat(resolved.getId())
                    .as("must resolve to the record the registry created, not a new one")
                    .isEqualTo(provisioned.getId());
            assertThat(resolved.getKeycloakSubject()).isEqualTo(subject);
        }

        /**
         * The takeover scenario. A second identity asserting a student number that already belongs to
         * a linked account must be refused outright — never relinked, and never silently given a new
         * account that shadows the first.
         */
        @Test
        @DisplayName("refuses a second identity claiming an already-linked student number")
        void refusesToRelinkAClaimedStudentNumber() {
            String tag = unique();
            String studentNumber = "20" + tag;
            User owner = userRepository.saveAndFlush(
                    User.fromIdentityProvider(
                            "original-subject-" + tag, "owner-" + tag, "owner-" + tag + "@university.test", "Real", "Owner"));
            studentRepository.saveAndFlush(new Student(
                    owner.getId(), studentNumber, fixtures.programme().getId(), LocalDate.of(2025, 9, 1)));

            assertThatThrownBy(() -> provisioningService.provision(
                            token("attacker-subject-" + tag, studentNumber, null, false, studentNumber)))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                    .isEqualTo(IdentityErrorCode.IDENTITY_CONFLICT);

            assertThat(userRepository.findById(owner.getId()).orElseThrow().getKeycloakSubject())
                    .as("the original link must be untouched")
                    .isEqualTo("original-subject-" + tag);
        }

        /** The refusal has to outlive the failed request, or an investigator has nothing to find. */
        @Test
        @DisplayName("records the refusal in the audit trail even though the request fails")
        void auditsTheRefusal() {
            String tag = unique();
            String studentNumber = "20" + tag;
            User owner = userRepository.saveAndFlush(User.fromIdentityProvider(
                    "original-" + tag, "owner-" + tag, "owner-" + tag + "@university.test", "Real", "Owner"));
            studentRepository.saveAndFlush(new Student(
                    owner.getId(), studentNumber, fixtures.programme().getId(), LocalDate.of(2025, 9, 1)));

            try {
                provisioningService.provision(token("attacker-" + tag, studentNumber, null, false, studentNumber));
            } catch (ApplicationException expected) {
                // The refusal is the point; the record it leaves behind is what is being asserted.
            }

            List<AuditEvent> events = auditEventRepository.findAll();
            assertThat(events)
                    .extracting(AuditEvent::getAction, AuditEvent::getEntityId)
                    .contains(org.assertj.core.groups.Tuple.tuple("IDENTITY_LINK_REFUSED", owner.getId()));
        }
    }

    @Nested
    @DisplayName("Correlation by email")
    class ByEmail {

        @Test
        @DisplayName("an unverified email never links an account")
        void unverifiedEmailDoesNotLink() {
            String tag = unique();
            String email = "unverified-" + tag + "@university.test";
            User existing = userRepository.saveAndFlush(new User("unv-" + tag, email, "Un", "Verified"));

            User resolved = provisioningService.provision(
                    token(UUID.randomUUID().toString(), "someone-" + tag, email, false, null));

            assertThat(resolved.getId())
                    .as("an unverified address is a claim the caller made about themselves")
                    .isNotEqualTo(existing.getId());
        }

        @Test
        @DisplayName("a verified email links an account that is not yet claimed")
        void verifiedEmailLinks() {
            String tag = unique();
            String email = "verified-" + tag + "@university.test";
            User existing = userRepository.saveAndFlush(new User("ver-" + tag, email, "Ver", "Ified"));

            String subject = UUID.randomUUID().toString();
            User resolved = provisioningService.provision(token(subject, "ver-" + tag, email, true, null));

            assertThat(resolved.getId()).isEqualTo(existing.getId());
            assertThat(resolved.getKeycloakSubject()).isEqualTo(subject);
        }
    }

    @Nested
    @DisplayName("First sight of an unknown identity")
    class JustInTime {

        @Test
        @DisplayName("provisions a local user, and no student record")
        void provisionsIdentityOnly() {
            String tag = unique();
            String subject = UUID.randomUUID().toString();

            User created = provisioningService.provision(
                    token(subject, "new-" + tag, "new-" + tag + "@university.test", true, null));

            assertThat(created.getKeycloakSubject()).isEqualTo(subject);
            assertThat(studentRepository.findByUserId(created.getId()))
                    .as("authentication must never imply an academic record")
                    .isEmpty();
        }

        /**
         * Two devices, one person, first login. The unique index is the guarantee; this asserts the
         * application turns the losing insert into the same answer rather than a 500.
         */
        @Test
        @DisplayName("concurrent first requests resolve to exactly one user")
        void concurrentFirstRequestsProduceOneUser() throws Exception {
            String tag = unique();
            String subject = UUID.randomUUID().toString();
            Jwt jwt = token(subject, "race-" + tag, "race-" + tag + "@university.test", true, null);

            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                List<Callable<UUID>> tasks = List.of(
                        () -> provisioningService.provision(jwt).getId(),
                        () -> provisioningService.provision(jwt).getId(),
                        () -> provisioningService.provision(jwt).getId(),
                        () -> provisioningService.provision(jwt).getId());
                List<Future<UUID>> results = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);

                assertThat(results)
                        .allSatisfy(future -> assertThat(future.get()).isNotNull())
                        .extracting(Future::get)
                        .containsOnly(results.get(0).get());
            } finally {
                pool.shutdownNow();
            }
            assertThat(userRepository.findByKeycloakSubject(subject)).isPresent();
        }
    }
}
