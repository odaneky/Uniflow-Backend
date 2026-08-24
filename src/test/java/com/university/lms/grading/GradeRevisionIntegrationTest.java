package com.university.lms.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.grading.domain.Grade;
import com.university.lms.grading.repository.GradeRepository;
import com.university.lms.grading.repository.GradeRevisionRepository;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code Grade.revise()} used to overwrite {@code percentage}/{@code letter}/{@code gradePoint} in
 * place, with nothing recording what the mark used to be. This is the coverage for what replaced
 * it: every award and change writes a {@code grade_revisions} row, a change to an already-awarded
 * grade must give a reason, and a locked grade refuses revision outright — see the C1 workstream in
 * {@code docs/} for why this exists.
 */
@AutoConfigureMockMvc
class GradeRevisionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OwnerScopingFixtures fixtures;

    @Autowired
    private GradeRevisionRepository gradeRevisionRepository;

    @Autowired
    private GradeRepository gradeRepository;

    private static RequestPostProcessor asLecturer(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "lecturer-" + subject.substring(0, 8))
                        .claim("email", "lecturer-" + subject.substring(0, 8) + "@university.test")
                        .claim("given_name", "Lee")
                        .claim("family_name", "Lecturer"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.LECTURER))});
    }

    private UUID award(UUID studentId, UUID sectionId, String percentage, String reason, RequestPostProcessor as)
            throws Exception {
        String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        String body = "{\"studentId\":\"" + studentId + "\",\"courseSectionId\":\"" + sectionId
                + "\",\"percentage\":" + percentage + reasonJson + "}";
        String response = mockMvc.perform(post("/api/v1/grades")
                        .with(as)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    @Test
    @DisplayName("the first award writes one revision with no before-value")
    void firstAwardWritesOneRevision() throws Exception {
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());

        UUID gradeId = award(student.studentId(), section, "72.00", null, asLecturer(teacher.subject()));

        List<com.university.lms.grading.domain.GradeRevision> revisions =
                gradeRevisionRepository.findByGradeIdOrderByRevisionNumberAsc(gradeId);
        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).getRevisionNumber()).isEqualTo(1);
        assertThat(revisions.get(0).getBeforePercentage()).isNull();
        assertThat(revisions.get(0).getAfterPercentage()).isEqualByComparingTo("72.00");
        assertThat(revisions.get(0).getReason()).isEqualTo("Initial award");
        assertThat(revisions.get(0).getChangedBy()).isEqualTo(teacher.userId());
    }

    @Test
    @DisplayName("changing an already-awarded grade without a reason is refused")
    void changeWithoutReasonIsRefused() throws Exception {
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        award(student.studentId(), section, "72.00", null, asLecturer(teacher.subject()));

        String body = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":80.00}";

        mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_REVISION_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("changing an already-awarded grade with a reason writes a second revision")
    void changeWithReasonWritesASecondRevision() throws Exception {
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        UUID gradeId = award(student.studentId(), section, "72.00", null, asLecturer(teacher.subject()));

        award(student.studentId(), section, "80.00", "Re-marked after a transcription error",
                asLecturer(teacher.subject()));

        List<com.university.lms.grading.domain.GradeRevision> revisions =
                gradeRevisionRepository.findByGradeIdOrderByRevisionNumberAsc(gradeId);
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(1).getRevisionNumber()).isEqualTo(2);
        assertThat(revisions.get(1).getBeforePercentage()).isEqualByComparingTo("72.00");
        assertThat(revisions.get(1).getAfterPercentage()).isEqualByComparingTo("80.00");
        assertThat(revisions.get(1).getReason()).isEqualTo("Re-marked after a transcription error");
    }

    @Test
    @DisplayName("a locked grade refuses revision")
    void lockedGradeRefusesRevision() throws Exception {
        OwnerScopingFixtures.Person teacher = fixtures.lecturer();
        OwnerScopingFixtures.Person student = fixtures.student();
        UUID section = fixtures.openSectionTaughtBy(teacher.userId());
        UUID gradeId = award(student.studentId(), section, "72.00", null, asLecturer(teacher.subject()));

        Grade grade = gradeRepository.findById(gradeId).orElseThrow();
        grade.lock(teacher.userId());
        gradeRepository.saveAndFlush(grade);

        String body = "{\"studentId\":\"" + student.studentId() + "\",\"courseSectionId\":\"" + section
                + "\",\"percentage\":90.00,\"reason\":\"Trying to change a locked grade\"}";

        mockMvc.perform(post("/api/v1/grades")
                        .with(asLecturer(teacher.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GRADE_LOCKED"));
    }
}
