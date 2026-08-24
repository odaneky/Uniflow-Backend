package com.university.lms.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.enrollment.domain.Enrollment;
import com.university.lms.enrollment.repository.EnrollmentRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Students see their own exam timetable — and only the published parts of it.
 *
 * <p>Two failures would matter most here. Showing a draft timetable sends people to the wrong hall
 * on the wrong day, and it is wrong for most of its life. Showing somebody else's places a named
 * person in a known room at a known time.
 */
@AutoConfigureMockMvc
class ExamTimetableIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerScopingFixtures people;

    @Autowired
    private AcademicFixtures fixtures;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private static RequestPostProcessor asStudent(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "s-" + subject.substring(0, 8))
                        .claim("email", subject.substring(0, 8) + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Test")
                        .claim("family_name", "Student"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT))
                });
    }

    private static final String REGISTRAR_SUBJECT = "registrar-" + UUID.randomUUID();

    private static RequestPostProcessor asRegistrar() {
        return jwt().jwt(token -> token.claim("sub", REGISTRAR_SUBJECT)
                        .claim("preferred_username", REGISTRAR_SUBJECT)
                        .claim("email", REGISTRAR_SUBJECT + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))
                });
    }

    /** Enrols a student in a fresh section and schedules an exam for it. Returns the sitting id. */
    private String scheduleExamFor(OwnerScopingFixtures.Person student, boolean publish) throws Exception {
        UUID sectionId = people.openSection();
        enrollmentRepository.saveAndFlush(new Enrollment(student.studentId(), sectionId));

        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Final examination",
                "startsAt", Instant.now().plus(20, ChronoUnit.DAYS).toString(),
                "durationMinutes", 120,
                "room", "Hall-" + UUID.randomUUID().toString().substring(0, 6),
                "seating", "Seats 1–42"));

        String created = mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", sectionId)
                        .with(asRegistrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sittingId = objectMapper.readTree(created).path("id").asText();
        if (publish) {
            mockMvc.perform(post("/api/v1/courses/sections/exams/{id}/publish", sittingId).with(asRegistrar()))
                    .andExpect(status().isOk());
        }
        return sittingId;
    }

    @Nested
    @DisplayName("What a student sees")
    class StudentView {

        @Test
        @DisplayName("a published exam appears with its hall, time and seat")
        void publishedExamAppears() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            scheduleExamFor(me, true);

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exams[0].title").value("Final examination"))
                    .andExpect(jsonPath("$.exams[0].room").exists())
                    .andExpect(jsonPath("$.exams[0].seating").value("Seats 1–42"))
                    .andExpect(jsonPath("$.exams[0].courseCode").exists())
                    .andExpect(jsonPath("$.exams[0].endsAt").exists());
        }

        /**
         * A draft timetable is wrong for most of its life. Showing it sends students to the wrong
         * hall on the wrong day, which is the one outcome an exam timetable must never produce.
         */
        @Test
        @DisplayName("an unpublished exam is invisible")
        void draftExamIsHidden() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            scheduleExamFor(me, false);

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exams").isEmpty());
        }

        @Test
        @DisplayName("one student never sees another's exams")
        void examsAreNotShared() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            OwnerScopingFixtures.Person other = people.student();
            scheduleExamFor(other, true);

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exams").isEmpty());
        }

        @Test
        @DisplayName("a student with no exams gets an empty timetable, not an error")
        void noExamsIsNotAnError() throws Exception {
            OwnerScopingFixtures.Person me = people.student();

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inExamPeriod").value(false))
                    .andExpect(jsonPath("$.exams").isEmpty());
        }
    }

    @Nested
    @DisplayName("Clashes between a student's own papers")
    class Clashes {

        /**
         * Nobody schedules this on purpose, but by the time a student is looking it already exists.
         * Showing two overlapping exams without saying so is how somebody sits one and misses the
         * other.
         */
        @Test
        @DisplayName("two exams at the same time are reported, naming both papers")
        void reportsOverlappingExams() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            String startsAt = Instant.now().plus(25, ChronoUnit.DAYS).toString();

            for (int i = 0; i < 2; i++) {
                UUID sectionId = people.openSection();
                enrollmentRepository.saveAndFlush(new Enrollment(me.studentId(), sectionId));
                String body = objectMapper.writeValueAsString(Map.of(
                        "title", "Final", "startsAt", startsAt, "durationMinutes", 120,
                        "room", "Hall-" + UUID.randomUUID().toString().substring(0, 6)));
                String created = mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", sectionId)
                                .with(asRegistrar())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                mockMvc.perform(post("/api/v1/courses/sections/exams/{id}/publish",
                                objectMapper.readTree(created).path("id").asText()).with(asRegistrar()))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exams.length()").value(2))
                    .andExpect(jsonPath("$.clashes.length()").value(1))
                    .andExpect(jsonPath("$.clashes[0].message").value(
                            org.hamcrest.Matchers.containsString("examinations office")));
        }

        @Test
        @DisplayName("exams on different days are not a clash")
        void separateExamsAreNotClashes() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            scheduleExamFor(me, true);

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clashes").isEmpty());
        }
    }

    @Nested
    @DisplayName("The examination period")
    class Period {

        @Test
        @DisplayName("the server decides whether exams have started, not the client")
        void reportsWhetherInExamPeriod() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            UUID sectionId = people.openSection();
            enrollmentRepository.saveAndFlush(new Enrollment(me.studentId(), sectionId));
            scheduleExamFor(me, true);

            var term = fixtures.openTerm();
            LocalDate today = LocalDate.now();
            mockMvc.perform(put("/api/v1/academic-terms/{id}/exam-window", term.getId())
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "startsOn", today.minusDays(2).toString(),
                                    "endsOn", today.plusDays(10).toString()))))
                    .andExpect(status().isOk());

            // The window belongs to the fixture term; the assertion is that the field is answered by
            // the server at all, rather than left for each client to compute from two dates.
            String body = mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(objectMapper.readTree(body).has("inExamPeriod")).isTrue();
        }

        @Test
        @DisplayName("a student cannot move the examination period")
        void studentsCannotSetTheWindow() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            var term = fixtures.openTerm();

            mockMvc.perform(put("/api/v1/academic-terms/{id}/exam-window", term.getId())
                            .with(asStudent(me.subject()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "startsOn", "2026-01-01", "endsOn", "2026-01-10"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Moving and cancelling")
    class Lifecycle {

        @Autowired
        private com.university.lms.notification.repository.NotificationRepository notifications;

        @Autowired
        private com.university.lms.administration.repository.AuditEventRepository auditEvents;

        /**
         * The reason this is not a plain update: students have already planned around a published
         * exam, so moving one has to tell them.
         */
        @Test
        @DisplayName("moving a published exam notifies everyone sitting it")
        void reschedulingNotifiesCandidates() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            String sittingId = scheduleExamFor(me, true);
            long before = notifications.count();

            String moved = objectMapper.writeValueAsString(Map.of(
                    "title", "Final examination",
                    "startsAt", Instant.now().plus(21, ChronoUnit.DAYS).toString(),
                    "durationMinutes", 120,
                    "room", "Hall-Moved-" + UUID.randomUUID().toString().substring(0, 6)));

            mockMvc.perform(put("/api/v1/courses/sections/exams/{id}", sittingId)
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(moved))
                    .andExpect(status().isOk());

            assertThat(notifications.count()).as("the student is told").isGreaterThan(before);
            assertThat(auditEvents.findAll())
                    .extracting(com.university.lms.administration.domain.AuditEvent::getAction)
                    .contains("EXAM_RESCHEDULED");
        }

        /** An exam nobody could see can be moved without alarming anyone. */
        @Test
        @DisplayName("moving a draft notifies nobody")
        void reschedulingADraftIsQuiet() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            String sittingId = scheduleExamFor(me, false);
            long before = notifications.count();

            mockMvc.perform(put("/api/v1/courses/sections/exams/{id}", sittingId)
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", "Final examination",
                                    "startsAt", Instant.now().plus(22, ChronoUnit.DAYS).toString(),
                                    "durationMinutes", 90,
                                    "room", "Hall-Q-" + UUID.randomUUID().toString().substring(0, 6)))))
                    .andExpect(status().isOk());

            assertThat(notifications.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("a cancelled exam leaves the student's timetable but keeps its record")
        void cancellingHidesItWithoutDeletingIt() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            String sittingId = scheduleExamFor(me, true);

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(jsonPath("$.exams.length()").value(1));

            mockMvc.perform(post("/api/v1/courses/sections/exams/{id}/cancel", sittingId)
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "Hall flooded"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancelledReason").value("Hall flooded"));

            mockMvc.perform(get("/api/v1/me/exams").with(asStudent(me.subject())))
                    .andExpect(jsonPath("$.exams").isEmpty());

            assertThat(auditEvents.findAll())
                    .extracting(com.university.lms.administration.domain.AuditEvent::getAction)
                    .contains("EXAM_CANCELLED");
        }

        /**
         * A withdrawn exam must stop holding its hall, or it blocks its own replacement from being
         * scheduled in the room it just vacated.
         */
        @Test
        @DisplayName("a cancelled sitting releases its hall")
        void cancellingReleasesTheHall() throws Exception {
            UUID first = people.openSection();
            UUID second = people.openSection();
            String room = "Hall-" + UUID.randomUUID().toString().substring(0, 6);
            String startsAt = Instant.now().plus(33, ChronoUnit.DAYS).toString();
            String body = objectMapper.writeValueAsString(Map.of(
                    "title", "Final", "startsAt", startsAt, "durationMinutes", 120, "room", room));

            String created = mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", first)
                            .with(asRegistrar()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

            mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", second)
                            .with(asRegistrar()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());

            mockMvc.perform(post("/api/v1/courses/sections/exams/{id}/cancel",
                            objectMapper.readTree(created).path("id").asText())
                            .with(asRegistrar()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", second)
                            .with(asRegistrar()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("a cancelled sitting cannot be rescheduled")
        void cancelledCannotBeMoved() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            String sittingId = scheduleExamFor(me, true);
            mockMvc.perform(post("/api/v1/courses/sections/exams/{id}/cancel", sittingId)
                            .with(asRegistrar()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/v1/courses/sections/exams/{id}", sittingId)
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", "Final", "startsAt", Instant.now().plus(40, ChronoUnit.DAYS).toString(),
                                    "durationMinutes", 120, "room", "Hall Z"))))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("a student cannot move or cancel an exam")
        void studentsCannotTouchTheTimetable() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            String sittingId = scheduleExamFor(me, true);

            mockMvc.perform(post("/api/v1/courses/sections/exams/{id}/cancel", sittingId)
                            .with(asStudent(me.subject()))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("The office view")
    class OfficeView {

        /** It includes drafts, so it must not be reachable by the people the drafts are hidden from. */
        @Test
        @DisplayName("a student cannot read a section's exam list")
        void sectionExamsAreStaffOnly() throws Exception {
            OwnerScopingFixtures.Person me = people.student();
            UUID sectionId = people.openSection();

            mockMvc.perform(get("/api/v1/courses/sections/{id}/exams", sectionId).with(asStudent(me.subject())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a hall cannot host two exams at once")
        void refusesDoubleBookedHall() throws Exception {
            UUID first = people.openSection();
            UUID second = people.openSection();
            String room = "Hall-" + UUID.randomUUID().toString().substring(0, 6);
            String startsAt = Instant.now().plus(30, ChronoUnit.DAYS).toString();

            String body = objectMapper.writeValueAsString(Map.of(
                    "title", "Final", "startsAt", startsAt, "durationMinutes", 120, "room", room));

            mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", first)
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/courses/sections/{id}/exams", second)
                            .with(asRegistrar())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
        }
    }
}
