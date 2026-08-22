package com.university.lms.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.domain.EnrollmentStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The enrolment lifecycle is the rule that protects academic records from being rewritten, so it
 * is tested as a state machine in its own right rather than only through the service.
 */
class EnrollmentStatusTest {

    @Nested
    @DisplayName("permitted transitions")
    class Permitted {

        @Test
        void pendingMayBeConfirmedOrAbandoned() {
            assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.ENROLLED)).isTrue();
            assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.DROPPED)).isTrue();
            assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.WITHDRAWN)).isTrue();
        }

        @Test
        void enrolledMayBeDroppedWithdrawnOrCompleted() {
            assertThat(EnrollmentStatus.ENROLLED.canTransitionTo(EnrollmentStatus.DROPPED)).isTrue();
            assertThat(EnrollmentStatus.ENROLLED.canTransitionTo(EnrollmentStatus.WITHDRAWN)).isTrue();
            assertThat(EnrollmentStatus.ENROLLED.canTransitionTo(EnrollmentStatus.COMPLETED)).isTrue();
        }

        @Test
        void waitlistedMayBePromotedOrLeft() {
            assertThat(EnrollmentStatus.WAITLISTED.canTransitionTo(EnrollmentStatus.ENROLLED)).isTrue();
            assertThat(EnrollmentStatus.WAITLISTED.canTransitionTo(EnrollmentStatus.DROPPED)).isTrue();
            assertThat(EnrollmentStatus.WAITLISTED.canTransitionTo(EnrollmentStatus.WITHDRAWN)).isFalse();
        }

        @Test
        void pendingMayNotSkipStraightToCompleted() {
            assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.COMPLETED)).isFalse();
        }
    }

    @Nested
    @DisplayName("terminal states")
    class Terminal {

        @ParameterizedTest
        @EnumSource(
                value = EnrollmentStatus.class,
                names = {"DROPPED", "WITHDRAWN", "COMPLETED"})
        void areTerminalAndAcceptNoFurtherTransition(EnrollmentStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            for (EnrollmentStatus target : EnrollmentStatus.values()) {
                assertThat(terminal.canTransitionTo(target))
                        .as("%s -> %s must be refused", terminal, target)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("a completed enrolment cannot be reopened")
        void completedCannotBeReopened() {
            Enrollment enrolment = new Enrollment(UUID.randomUUID(), UUID.randomUUID());
            enrolment.transitionTo(EnrollmentStatus.COMPLETED);

            assertThatThrownBy(() -> enrolment.transitionTo(EnrollmentStatus.ENROLLED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED");
        }
    }

    @Nested
    @DisplayName("seat occupancy")
    class SeatOccupancy {

        @Test
        void pendingAndEnrolledHoldASeat() {
            assertThat(EnrollmentStatus.PENDING.occupiesSeat()).isTrue();
            assertThat(EnrollmentStatus.ENROLLED.occupiesSeat()).isTrue();
        }

        @Test
        void endedRegistrationsReleaseTheirSeat() {
            assertThat(EnrollmentStatus.DROPPED.occupiesSeat()).isFalse();
            assertThat(EnrollmentStatus.WITHDRAWN.occupiesSeat()).isFalse();
            assertThat(EnrollmentStatus.WAITLISTED.occupiesSeat()).isFalse();
            assertThat(EnrollmentStatus.WAITLISTED.occupiesTimetable()).isTrue();
        }
    }

    @Test
    @DisplayName("re-applying the current status is a no-op, not an error")
    void selfTransitionIsIgnored() {
        Enrollment enrolment = new Enrollment(UUID.randomUUID(), UUID.randomUUID());
        enrolment.transitionTo(EnrollmentStatus.ENROLLED);

        assertThat(enrolment.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(enrolment.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("reaching a terminal state stamps the end time")
    void terminalTransitionRecordsEndedAt() {
        Enrollment enrolment = new Enrollment(UUID.randomUUID(), UUID.randomUUID());
        enrolment.transitionTo(EnrollmentStatus.DROPPED);

        assertThat(enrolment.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);
        assertThat(enrolment.getEndedAt()).isNotNull();
    }
}
