package com.university.lms.course.api;

import com.university.lms.course.api.CourseCatalog.Meeting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Shared clock rules for occurrence meetings: overlap, and Lecture → Tutorial → Lab order.
 *
 * <p>Lives on the course contract so enrolment can ask whether a set of meetings is legal without
 * duplicating campus-week arithmetic. ISO {@code dayOfWeek}: 1 = Monday … 5 = Friday.
 */
public final class Timetable {

    /** Thursday in ISO weekday numbering — late enough that a follow-on can fall the next week. */
    static final int LATE_WEEK_DAY = 4;

    /** Wednesday — early enough to read as the next week's cycle. */
    static final int EARLY_WEEK_DAY = 3;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm");

    private Timetable() {}

    public static boolean overlaps(Meeting a, Meeting b) {
        if (a == null || b == null || a.dayOfWeek() != b.dayOfWeek()) {
            return false;
        }
        LocalTime startA = parse(a.startTime());
        LocalTime endA = parse(a.endTime());
        LocalTime startB = parse(b.startTime());
        LocalTime endB = parse(b.endTime());
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    /** True when two sessions on the same occurrence share a clock interval. */
    public static boolean selfClash(List<Meeting> meetings) {
        if (meetings == null || meetings.size() < 2) {
            return false;
        }
        for (int i = 0; i < meetings.size(); i++) {
            for (int j = i + 1; j < meetings.size(); j++) {
                if (overlaps(meetings.get(i), meetings.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean clashes(List<Meeting> left, List<Meeting> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (Meeting a : left) {
            for (Meeting b : right) {
                if (overlaps(a, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Lecture, then Tutorial, then Lab (later types optional). Each inner list is one occurrence.
     *
     * <p>A type "starts" at the latest of its per-occurrence earliest days, matching the product
     * rule that a student may land in any combination of streams. Wrap-around: an earlier type that
     * reaches Thursday/Friday may be followed by a later type that starts Monday–Wednesday.
     */
    public static Optional<String> componentOrderIssue(Collection<List<Meeting>> occurrences) {
        TypeSpan lecture = typeSpan(occurrences, "Lecture");
        TypeSpan tutorial = typeSpan(occurrences, "Tutorial");
        TypeSpan lab = typeSpan(occurrences, "Lab");

        if (lecture != null && tutorial != null && !precedes(lecture, tutorial)) {
            return Optional.of(
                    "Every Lecture section must start before every Tutorial section that week (or run late enough in the week that the tutorial can follow early the next week).");
        }
        TypeSpan beforeLab = tutorial != null ? tutorial : lecture;
        if (beforeLab != null && lab != null && !precedes(beforeLab, lab)) {
            if (tutorial != null) {
                return Optional.of(
                        "Every Tutorial section must start before every Lab section that week (or run late enough to carry into early the next week).");
            }
            return Optional.of(
                    "Every Lecture section must start before every Lab section that week (or run late enough to carry into early the next week).");
        }
        return Optional.empty();
    }

    private static boolean precedes(TypeSpan earlier, TypeSpan later) {
        return earlier.start <= later.start
                || (earlier.reach >= LATE_WEEK_DAY && later.start <= EARLY_WEEK_DAY);
    }

    private static TypeSpan typeSpan(Collection<List<Meeting>> occurrences, String type) {
        if (occurrences == null) {
            return null;
        }
        List<int[]> spans = new ArrayList<>();
        for (List<Meeting> meetings : occurrences) {
            if (meetings == null) {
                continue;
            }
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            boolean found = false;
            for (Meeting meeting : meetings) {
                if (meeting == null || meeting.sessionType() == null) {
                    continue;
                }
                if (!type.equalsIgnoreCase(meeting.sessionType().trim())) {
                    continue;
                }
                found = true;
                min = Math.min(min, meeting.dayOfWeek());
                max = Math.max(max, meeting.dayOfWeek());
            }
            if (found) {
                spans.add(new int[] {min, max});
            }
        }
        if (spans.isEmpty()) {
            return null;
        }
        int start = Integer.MIN_VALUE;
        int reach = Integer.MIN_VALUE;
        for (int[] span : spans) {
            start = Math.max(start, span[0]);
            reach = Math.max(reach, span[1]);
        }
        return new TypeSpan(start, reach);
    }

    private static LocalTime parse(String clock) {
        if (clock == null || clock.isBlank()) {
            return null;
        }
        String value = clock.trim();
        try {
            return LocalTime.parse(value, CLOCK);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalTime.parse(value);
            } catch (DateTimeParseException again) {
                return null;
            }
        }
    }

    private record TypeSpan(int start, int reach) {}
}
