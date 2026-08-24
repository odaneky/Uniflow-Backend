package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.course.dto.ReplaceSectionMeetingsRequest;
import com.university.lms.course.dto.ReplaceSectionMeetingsRequest.MeetingRequest;
import com.university.lms.course.service.CourseService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Session hours are wall-clock values. Hibernate's default TIME binding applies
 * {@code hibernate.jdbc.time_zone} (UTC) through {@code java.sql.Time}, which on a UTC−5 JVM turns
 * 16:00–19:00 into 21:00–00:00 and trips {@code ck_section_meetings_span}.
 */
class SectionMeetingPersistenceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private AcademicFixtures fixtures;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("an afternoon lab is stored as 16:00–19:00 even when the JVM is UTC−5")
    void afternoonLabIsNotShiftedOvernight() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Jamaica"));
        try {
            var section = fixtures.openSection(fixtures.openTerm(), 40);
            courseService.replaceMeetings(
                    section.getId(),
                    new ReplaceSectionMeetingsRequest(
                            List.of(new MeetingRequest(
                                    3, LocalTime.of(16, 0), LocalTime.of(19, 0), "LAB-01", "Lab")),
                            null));

            var row = jdbcTemplate.queryForMap(
                    "select start_time::text as start, end_time::text as \"end\" from section_meetings where section_id = ?",
                    section.getId());

            assertThat(row.get("start").toString()).startsWith("16:00");
            assertThat(row.get("end").toString()).startsWith("19:00");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("a Saturday class can be stored — G1: evening/continuing-ed sections meet on Saturdays")
    void saturdayMeetingIsStored() {
        var section = fixtures.openSection(fixtures.openTerm(), 40);
        courseService.replaceMeetings(
                section.getId(),
                new ReplaceSectionMeetingsRequest(
                        List.of(new MeetingRequest(
                                6, LocalTime.of(9, 0), LocalTime.of(12, 0), "LAB-01", "Lab")),
                        null));

        var row = jdbcTemplate.queryForMap(
                "select day_of_week from section_meetings where section_id = ?", section.getId());

        assertThat(row.get("day_of_week")).isEqualTo(6);
    }
}
