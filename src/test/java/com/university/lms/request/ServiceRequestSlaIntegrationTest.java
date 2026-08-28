package com.university.lms.request;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.security.OwnerScopingFixtures;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * D9: SLA due-date tracking, staff escalation, staff-to-staff reassignment, and inbound
 * student-submitted evidence for service requests — end to end through the real HTTP surface.
 */
@AutoConfigureMockMvc
class ServiceRequestSlaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerScopingFixtures people;

    private static RequestPostProcessor registrar(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))
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

    /** Not the assigned staff member and not a queue manager — the reassign guard's negative case. */
    private static RequestPostProcessor otherLecturer(String subject) {
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("email_verified", true)
                        .claim("given_name", "Lee")
                        .claim("family_name", "Lecturer"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.LECTURER))
                });
    }

    private String createTranscriptRequest(String studentSubject) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "type", "TRANSCRIPT", "payload", Map.of("deliveryMethod", "MAIL")));
        String created = mockMvc.perform(post("/api/v1/me/requests")
                        .with(asStudent(studentSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(created).path("id").asText();
    }

    @Test
    @DisplayName("a submitted request gets a dueAt in the future, computed from its type's SLA window")
    void creatingSetsADueDateFromTheTypesSla() throws Exception {
        OwnerScopingFixtures.Person student = people.student();

        mockMvc.perform(get("/api/v1/me/requests/{id}", createTranscriptRequest(student.subject()))
                        .with(asStudent(student.subject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").exists())
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.escalated").value(false))
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments.length()").value(0));
    }

    @Test
    @DisplayName("a registrar escalates a request, recording who, when and why")
    void escalatingRecordsWhoWhenAndWhy() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String requestId = createTranscriptRequest(student.subject());
        String registrarSubject = "registrar-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/requests/{id}/escalate", requestId)
                        .with(registrar(registrarSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Student graduates next week"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalated").value(true))
                .andExpect(jsonPath("$.escalatedAt").exists())
                .andExpect(jsonPath("$.escalationReason").value("Student graduates next week"));
    }

    @Test
    @DisplayName("escalating an already-cancelled request is refused")
    void escalatingAClosedRequestIsRefused() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String requestId = createTranscriptRequest(student.subject());
        mockMvc.perform(post("/api/v1/me/requests/{id}/cancel", requestId).with(asStudent(student.subject())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/escalate", requestId)
                        .with(registrar("registrar-" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Too late"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_CLOSED"));
    }

    @Test
    @DisplayName("the assigned staff member can reassign a request to a different staff member")
    void assignedStaffCanReassign() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        OwnerScopingFixtures.Person target = people.lecturer();
        String requestId = createTranscriptRequest(student.subject());
        String registrarSubject = "registrar-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/requests/{id}/claim", requestId).with(registrar(registrarSubject)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/reassign", requestId)
                        .with(registrar(registrarSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("toUserId", target.userId(), "note", "Covering for me this week"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value(target.userId().toString()));
    }

    @Test
    @DisplayName("staff who neither hold the request nor manage the queue cannot reassign it")
    void unrelatedStaffCannotReassign() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        OwnerScopingFixtures.Person target = people.lecturer();
        String requestId = createTranscriptRequest(student.subject());
        String registrarSubject = "registrar-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/requests/{id}/claim", requestId).with(registrar(registrarSubject)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/reassign", requestId)
                        .with(otherLecturer("lecturer-" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("toUserId", target.userId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a student attaches evidence, then both the student and staff can download it")
    void studentAttachesEvidenceAndBothPartiesCanDownloadIt() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String requestId = createTranscriptRequest(student.subject());
        MockMultipartFile file =
                new MockMultipartFile("file", "note.pdf", "application/pdf", "evidence".getBytes());

        String updated = mockMvc.perform(multipart("/api/v1/me/requests/{id}/attachments", requestId)
                        .file(file)
                        .with(asStudent(student.subject())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].fileName").value("note.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId =
                objectMapper.readTree(updated).path("attachments").path(0).path("documentId").asText();

        mockMvc.perform(get("/api/v1/me/requests/{id}/attachments/{docId}/download", requestId, documentId)
                        .with(asStudent(student.subject())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("note.pdf")));

        mockMvc.perform(get("/api/v1/requests/{id}/attachments/{docId}/download", requestId, documentId)
                        .with(registrar("registrar-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("note.pdf")));
    }

    @Test
    @DisplayName("an attachment of a disallowed content type is refused")
    void disallowedContentTypeIsRefused() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String requestId = createTranscriptRequest(student.subject());
        MockMultipartFile file =
                new MockMultipartFile("file", "script.exe", "application/x-msdownload", "bad".getBytes());

        mockMvc.perform(multipart("/api/v1/me/requests/{id}/attachments", requestId)
                        .file(file)
                        .with(asStudent(student.subject())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_ATTACHMENT_TYPE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("attaching evidence to an already-cancelled request is refused")
    void attachingToAClosedRequestIsRefused() throws Exception {
        OwnerScopingFixtures.Person student = people.student();
        String requestId = createTranscriptRequest(student.subject());
        mockMvc.perform(post("/api/v1/me/requests/{id}/cancel", requestId).with(asStudent(student.subject())))
                .andExpect(status().isOk());
        MockMultipartFile file =
                new MockMultipartFile("file", "note.pdf", "application/pdf", "evidence".getBytes());

        mockMvc.perform(multipart("/api/v1/me/requests/{id}/attachments", requestId)
                        .file(file)
                        .with(asStudent(student.subject())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_CLOSED"));
    }

    @Test
    @DisplayName("a student cannot attach evidence to someone else's request")
    void aStudentCannotAttachToAnotherStudentsRequest() throws Exception {
        OwnerScopingFixtures.Person owner = people.student();
        OwnerScopingFixtures.Person intruder = people.student();
        String requestId = createTranscriptRequest(owner.subject());
        MockMultipartFile file =
                new MockMultipartFile("file", "note.pdf", "application/pdf", "evidence".getBytes());

        mockMvc.perform(multipart("/api/v1/me/requests/{id}/attachments", requestId)
                        .file(file)
                        .with(asStudent(intruder.subject())))
                .andExpect(status().isNotFound());
    }
}
