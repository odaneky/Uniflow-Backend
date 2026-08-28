package com.university.lms.financialaid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.Map;
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
 * E9: named scholarship programmes end to end — catalog CRUD, awarding, acceptance, renewal into a
 * new term, and the partial-unique-index relaxation (V99) that lets a student hold more than one
 * scholarship at once while still refusing a duplicate award of the *same* programme in one term.
 */
@AutoConfigureMockMvc
class ScholarshipProgrammeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerScopingFixtures people;

    @Autowired
    private AcademicFixtures academicFixtures;

    private static RequestPostProcessor financialAidOfficer() {
        String subject = "faid-officer-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Farah")
                        .claim("family_name", "Aid"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.FINANCIAL_AID_OFFICER))
                });
    }

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

    private String createProgramme(String name, boolean renewable) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "sponsorName", "Acme Foundation",
                "defaultAmount", 2500.00,
                "renewable", renewable,
                "maxRenewals", 5));
        String created = mockMvc.perform(post("/api/v1/scholarship-programmes")
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(created).path("id").asText();
    }

    @Test
    @DisplayName("a financial aid officer creates a programme, awards it, and the student accepts it")
    void creatingAwardingAndAcceptingAScholarship() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String programmeId = createProgramme("Dean's Merit Award " + UUID.randomUUID(), true);

        String awarded = mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "AWARD_SCHOLARSHIP",
                                "academicTermId", people.openTermId(),
                                "scholarshipProgrammeId", programmeId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].awardType").value("SCHOLARSHIP"))
                .andExpect(jsonPath("$[0].amount").value(2500.00))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String awardId = objectMapper.readTree(awarded).path(0).path("id").asText();

        mockMvc.perform(post("/api/v1/me/financial-aid/awards/{id}/accept", awardId).with(asStudent(student.subject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("an accepted scholarship renews into a new term, carrying the link back")
    void renewingAnAcceptedScholarship() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String programmeId = createProgramme("Renewable Trustee Award " + UUID.randomUUID(), true);

        String awarded = mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "AWARD_SCHOLARSHIP",
                                "academicTermId", people.openTermId(),
                                "scholarshipProgrammeId", programmeId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String awardId = objectMapper.readTree(awarded).path(0).path("id").asText();

        mockMvc.perform(post("/api/v1/me/financial-aid/awards/{id}/accept", awardId).with(asStudent(student.subject())))
                .andExpect(status().isOk());

        AcademicTerm nextTerm = academicFixtures.openTerm();

        mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "RENEW_SCHOLARSHIP",
                                "awardId", awardId,
                                "academicTermId", nextTerm.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].awardType").value("SCHOLARSHIP"))
                .andExpect(jsonPath("$[0].renewedFromAwardId").value(awardId))
                .andExpect(jsonPath("$[0].academicTermId").value(nextTerm.getId().toString()));
    }

    @Test
    @DisplayName(
            "V99: a student may hold two different scholarships in one term, but not the same programme twice")
    void twoDifferentScholarshipsInOneTermAreBothAllowed() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String programmeA = createProgramme("Programme A " + UUID.randomUUID(), false);
        String programmeB = createProgramme("Programme B " + UUID.randomUUID(), false);
        UUID termId = people.openTermId();

        mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "AWARD_SCHOLARSHIP", "academicTermId", termId, "scholarshipProgrammeId", programmeA))))
                .andExpect(status().isOk());

        String secondAward = mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "AWARD_SCHOLARSHIP", "academicTermId", termId, "scholarshipProgrammeId", programmeB))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(secondAward).path(0).path("scholarshipProgrammeId").asText())
                .isEqualTo(programmeB);

        String listed = mockMvc.perform(get("/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(listed)).hasSize(2);
    }

    @Test
    @DisplayName("a non-renewable programme refuses renewal")
    void aNonRenewableProgrammeRefusesRenewal() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String programmeId = createProgramme("One-Time Award " + UUID.randomUUID(), false);

        String awarded = mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "AWARD_SCHOLARSHIP",
                                "academicTermId", people.openTermId(),
                                "scholarshipProgrammeId", programmeId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String awardId = objectMapper.readTree(awarded).path(0).path("id").asText();
        mockMvc.perform(post("/api/v1/me/financial-aid/awards/{id}/accept", awardId).with(asStudent(student.subject())))
                .andExpect(status().isOk());

        AcademicTerm nextTerm = academicFixtures.openTerm();
        mockMvc.perform(post(
                                "/api/v1/financial-aid/students/{studentId}/awards", student.studentId())
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "action", "RENEW_SCHOLARSHIP", "awardId", awardId, "academicTermId", nextTerm.getId()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCHOLARSHIP_NOT_RENEWABLE"));
    }

    @Test
    @DisplayName("a student cannot manage the scholarship-programme catalog")
    void aStudentCannotManageTheCatalog() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Should Fail " + UUID.randomUUID(), "defaultAmount", 100.00, "renewable", false));

        mockMvc.perform(post("/api/v1/scholarship-programmes")
                        .with(asStudent(student.subject()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("any authenticated user can browse the scholarship-programme catalog")
    void anyAuthenticatedUserCanBrowseTheCatalog() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        createProgramme("Browsable Award " + UUID.randomUUID(), false);

        mockMvc.perform(get("/api/v1/scholarship-programmes").with(asStudent(student.subject())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH updates a programme's fields, and a null field leaves it unchanged")
    void patchUpdatesFields() throws Exception {
        String programmeId = createProgramme("Editable Award " + UUID.randomUUID(), false);

        mockMvc.perform(patch("/api/v1/scholarship-programmes/{id}", programmeId)
                        .with(financialAidOfficer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("defaultAmount", 3000.00))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAmount").value(3000.00))
                .andExpect(jsonPath("$.sponsorName").value("Acme Foundation"));
    }
}
