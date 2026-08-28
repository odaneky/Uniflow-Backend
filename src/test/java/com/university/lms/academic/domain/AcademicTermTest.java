package com.university.lms.academic.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Registration policy: adding a new course is only ever allowed during priority REGISTRATION.
 * Once add/drop opens, a student may still drop (or be dropped) without penalty, but may not
 * select anything new — that window exists to let a student who over- or mis-registered correct
 * their schedule downward, not to keep registration open a second time. This used to be
 * {@code canAddAt(moment) == isRegistrationOpenAt(moment) || isAddDropOpenAt(moment)}, which let
 * add/drop double as a second add window; {@link #cannotAddDuringAddDrop()} pins the fix.
 */
class AcademicTermTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private AcademicTerm term() {
        AcademicYear year = new AcademicYear("2026/2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31));
        return new AcademicTerm(
                year, "Semester 1", TermType.SEMESTER, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 15));
    }

    @Test
    void canAddDuringPriorityRegistration() {
        AcademicTerm term = term();
        term.openRegistration(NOW.minus(5, ChronoUnit.DAYS), NOW.plus(5, ChronoUnit.DAYS));

        assertThat(term.canAddAt(NOW)).isTrue();
        assertThat(term.phaseAt(NOW)).isEqualTo("REGISTRATION");
    }

    @Test
    void cannotAddDuringAddDrop() {
        AcademicTerm term = term();
        term.openRegistration(NOW.minus(20, ChronoUnit.DAYS), NOW.minus(10, ChronoUnit.DAYS));
        term.openAddDrop(NOW.minus(2, ChronoUnit.DAYS), NOW.plus(5, ChronoUnit.DAYS), LocalDate.of(2026, 10, 1));

        assertThat(term.phaseAt(NOW)).isEqualTo("ADD_DROP");
        assertThat(term.canAddAt(NOW)).isFalse();
    }

    @Test
    void canStillDropWithoutPenaltyDuringAddDrop() {
        AcademicTerm term = term();
        term.openRegistration(NOW.minus(20, ChronoUnit.DAYS), NOW.minus(10, ChronoUnit.DAYS));
        term.openAddDrop(NOW.minus(2, ChronoUnit.DAYS), NOW.plus(5, ChronoUnit.DAYS), LocalDate.of(2026, 10, 1));

        assertThat(term.canDropWithoutPenaltyAt(NOW)).isTrue();
    }

    @Test
    void cannotAddOnceAddDropHasClosed() {
        AcademicTerm term = term();
        term.openRegistration(NOW.minus(20, ChronoUnit.DAYS), NOW.minus(15, ChronoUnit.DAYS));
        term.openAddDrop(NOW.minus(14, ChronoUnit.DAYS), NOW.minus(7, ChronoUnit.DAYS), LocalDate.of(2026, 10, 1));

        assertThat(term.phaseAt(NOW)).isEqualTo("WITHDRAW_ONLY");
        assertThat(term.canAddAt(NOW)).isFalse();
    }

    @Test
    void aTermWithNoConfiguredWindowsIsClosed() {
        // Outside the term's own in-session dates too, so phaseAt can't fall through to
        // WITHDRAW_ONLY (in-session with no windows configured is still "closed", never "add").
        AcademicYear year = new AcademicYear("2027/2028", LocalDate.of(2027, 8, 1), LocalDate.of(2028, 5, 31));
        AcademicTerm term = new AcademicTerm(
                year, "Semester 1", TermType.SEMESTER, 1, LocalDate.of(2027, 9, 1), LocalDate.of(2027, 12, 15));

        assertThat(term.canAddAt(NOW)).isFalse();
        assertThat(term.phaseAt(NOW)).isEqualTo("CLOSED");
    }
}
