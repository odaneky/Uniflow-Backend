package com.university.lms.admissions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ProgrammeApplicationFormIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Test
    void defaultFormIsPublicAndCustomizableByRegistrar() throws Exception {
        // A dedicated programme, not "whichever one sorts first" — that was fragile against any
        // other test in the same run having already customized the first-sorted programme's form.
        String id = academicFixtures.programme().getId().toString();

        mockMvc.perform(get("/api/v1/programmes/" + id + "/application-form"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programmeId").value(id))
                .andExpect(jsonPath("$.customized").value(false))
                .andExpect(jsonPath("$.fields[?(@.key=='personalStatement')]").exists());

        mockMvc.perform(put("/api/v1/programmes/" + id + "/application-form")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "fields": [
                                    {
                                      "key": "previousSchool",
                                      "label": "High school attended",
                                      "type": "TEXT",
                                      "section": "DETAILS",
                                      "required": true,
                                      "sortOrder": 0
                                    }
                                  ]
                                }
                                """)
                        .with(jwt().authorities(new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.fields[0].label").value("High school attended"));
    }
}
