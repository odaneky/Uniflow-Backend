package com.university.lms.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.university.lms.academic.domain.Programme;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
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
 * A published curriculum version's requirement blocks must stay exactly as they were published —
 * that is the property a degree audit resolved against a bound version depends on. Before this,
 * {@code programme_requirement_blocks} belonged directly to a programme with no concept of
 * versioning at all, so editing a requirement block always rewrote the live programme, silently
 * changing the answer for every student ever assessed against it.
 */
@AutoConfigureMockMvc
class CurriculumVersioningIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicFixtures academicFixtures;

    private static RequestPostProcessor asRegistrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))});
    }

    private UUID createBlock(UUID programmeId, String name, RequestPostProcessor as) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"kind\":\"CORE\",\"requiredCredits\":3}";
        String response = mockMvc.perform(post("/api/v1/programmes/{id}/requirement-blocks", programmeId)
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
    void publishingFreezesTheDraftsBlocks() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        UUID programmeId = academicFixtures.programme().getId();

        UUID blockId = createBlock(programmeId, "Core CS", registrar);

        mockMvc.perform(post("/api/v1/programmes/{id}/requirement-blocks/publish", programmeId).with(registrar))
                .andExpect(status().isNoContent());

        // Refused: the block's version is now published, not draft.
        mockMvc.perform(delete("/api/v1/programmes/{id}/requirement-blocks/{blockId}", programmeId, blockId)
                        .with(registrar))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRICULUM_VERSION_NOT_EDITABLE"));

        mockMvc.perform(post(
                                "/api/v1/programmes/{id}/requirement-blocks/{blockId}/courses",
                                programmeId,
                                blockId)
                        .with(registrar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRICULUM_VERSION_NOT_EDITABLE"));
    }

    @Test
    void aNewBlockAfterPublishingLandsInAFreshDraftNotTheLiveVersion() throws Exception {
        RequestPostProcessor registrar = asRegistrar();
        UUID programmeId = academicFixtures.programme().getId();

        createBlock(programmeId, "Core CS", registrar);
        mockMvc.perform(post("/api/v1/programmes/{id}/requirement-blocks/publish", programmeId).with(registrar))
                .andExpect(status().isNoContent());

        // A block created after publishing must succeed (auto-creates a new draft)...
        createBlock(programmeId, "Elective CS", registrar);

        // ...but the published, student-facing list is unaffected by it.
        mockMvc.perform(get("/api/v1/programmes/{id}/requirement-blocks", programmeId).with(registrar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Core CS"));
    }

    @Test
    void anUnpublishedProgrammeHasNoVisibleBlocks() throws Exception {
        Programme programme = academicFixtures.programme();

        mockMvc.perform(get("/api/v1/programmes/{id}/requirement-blocks", programme.getId()).with(asRegistrar()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
