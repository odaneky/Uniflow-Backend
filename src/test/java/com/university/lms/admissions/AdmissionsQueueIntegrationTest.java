package com.university.lms.admissions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.lms.common.security.SecurityRoles;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdmissionsQueueIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void queueReturnsEmptyPageForRegistrar() throws Exception {
        mockMvc.perform(get("/api/v1/admissions/queue")
                        .param("page", "0")
                        .param("size", "50")
                        .param("sort", "updatedAt,desc")
                        .param("status", "SUBMITTED")
                        .param("status", "IN_REVIEW")
                        .with(jwt().authorities(new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /**
     * {@code AdmissionsService.findById} used to have no guard at all — a random UUID was enough to
     * prove it, since a non-staff caller must be refused before the existence check even runs. If
     * this regresses to the pre-fix behaviour, a nonexistent id would surface as 404 instead of 403
     * for a student caller.
     */
    @Test
    void byIdIsRefusedToAStudent() throws Exception {
        mockMvc.perform(get("/api/v1/admissions/applications/" + java.util.UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void byIdIsReachableByRegistrarEvenWhenNotFound() throws Exception {
        // Confirms the guard runs before the lookup without needing a real application fixture:
        // a registrar reaches the 404 branch, not a 403 — the opposite of the student case above.
        mockMvc.perform(get("/api/v1/admissions/applications/" + java.util.UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR)))))
                .andExpect(status().isNotFound());
    }
}
