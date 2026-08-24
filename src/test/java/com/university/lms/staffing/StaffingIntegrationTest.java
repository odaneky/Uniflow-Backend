package com.university.lms.staffing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.identity.domain.User;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A4's actual deliverable: a self-referencing org tree, and a record of who is appointed where.
 * Nothing consults this to restrict access yet (that is A5) — this only proves the scope source
 * itself is correctly built and queryable.
 */
@AutoConfigureMockMvc
class StaffingIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private StaffAppointments staffAppointments;

    private static RequestPostProcessor asRegistrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))});
    }

    private static RequestPostProcessor asStudent(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", "student-" + subject.substring(0, 8))
                        .claim("email", subject.substring(0, 8) + "@university.test")
                        .claim("given_name", "Test")
                        .claim("family_name", "Student"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT))});
    }

    private UUID createOrgUnit(String parentIdOrNull, String code, String name, String unitType, RequestPostProcessor as)
            throws Exception {
        String parentField = parentIdOrNull == null ? "" : "\"parentId\":\"" + parentIdOrNull + "\",";
        String body = "{" + parentField + "\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"unitType\":\""
                + unitType + "\"}";
        String response = mockMvc.perform(post("/api/v1/org-units")
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
    void createsANestedOrgUnitTree() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UUID institutionId = createOrgUnit(null, "INST-" + suffix, "The University", "INSTITUTION", registrar);
        UUID facultyId = createOrgUnit(institutionId.toString(), "FAC-" + suffix, "Faculty of Science", "FACULTY", registrar);
        createOrgUnit(facultyId.toString(), "DEPT-" + suffix, "Department of Computing", "DEPARTMENT", registrar);

        mockMvc.perform(get("/api/v1/org-units/{id}/children", institutionId).with(registrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("FAC-" + suffix));

        mockMvc.perform(get("/api/v1/org-units/{id}/children", facultyId).with(registrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("DEPT-" + suffix));
    }

    @Test
    void appointingAndEndingStaffChangesWhatIsActiveToday() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        User lecturer = academicFixtures.user();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orgUnitId = createOrgUnit(null, "DEPT-" + suffix, "Department of Computing", "DEPARTMENT", registrar);

        String appointBody = "{\"userId\":\"" + lecturer.getId() + "\",\"orgUnitId\":\"" + orgUnitId
                + "\",\"role\":\"LECTURER\",\"validFrom\":\"2020-01-01\"}";
        String response = mockMvc.perform(post("/api/v1/staff-appointments")
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID appointmentId = UUID.fromString(JsonPath.read(response, "$.id"));

        assertThat(staffAppointments.activeAppointmentsOf(lecturer.getId()))
                .anyMatch(a -> a.orgUnitId().equals(orgUnitId) && a.role().equals("LECTURER"));

        mockMvc.perform(post("/api/v1/staff-appointments/{id}/end", appointmentId)
                        .param("validTo", LocalDate.now().minusDays(1).toString())
                        .with(registrar))
                .andExpect(status().isNoContent());

        assertThat(staffAppointments.activeAppointmentsOf(lecturer.getId())).isEmpty();
    }

    @Test
    void registeringAnEmployeeTwiceIsRefused() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        User employee = academicFixtures.user();

        String body = "{\"userId\":\"" + employee.getId() + "\",\"contractType\":\"FULL_TIME\",\"fte\":1.00}";
        mockMvc.perform(post("/api/v1/employees")
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/employees")
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_ALREADY_REGISTERED"));
    }

    @Test
    void aStudentMayNotCreateAnOrgUnit() throws Exception {
        String subject = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/org-units")
                        .with(asStudent(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\",\"unitType\":\"DEPARTMENT\"}"))
                .andExpect(status().isForbidden());
    }
}
