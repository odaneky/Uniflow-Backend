package com.university.lms.curriculum;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.course.domain.Course;
import com.university.lms.curriculum.domain.CourseSubstitution;
import com.university.lms.curriculum.repository.CourseSubstitutionRepository;
import com.university.lms.request.domain.ServiceRequest;
import com.university.lms.request.domain.ServiceRequestType;
import com.university.lms.request.repository.ServiceRequestRepository;
import com.university.lms.student.domain.Student;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * The read side of {@code course_substitutions}: a programme's list of granted exceptions, scoped
 * to the courses its active curriculum actually requires. Before this endpoint the rows were
 * written by the request-fulfilment workflow and consulted only inside the degree audit — nothing
 * surfaced them for a registrar reviewing a programme's curriculum.
 */
@AutoConfigureMockMvc
class CourseSubstitutionReadIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private CourseSubstitutionRepository substitutionRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    private static RequestPostProcessor asRegistrar() {
        String subject = "registrar-" + UUID.randomUUID();
        return jwt().jwt(token -> token.claim("sub", subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@university.test")
                        .claim("given_name", "Rita")
                        .claim("family_name", "Registrar"))
                .authorities(new GrantedAuthority[] {
                    new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.REGISTRAR))
                });
    }

    private static RequestPostProcessor asStudent() {
        return jwt().authorities(new GrantedAuthority[] {
            new SimpleGrantedAuthority(SecurityRoles.authority(SecurityRoles.STUDENT))
        });
    }

    private void createPublishedBlockWithCourse(UUID programmeId, UUID courseId) throws Exception {
        mockMvc.perform(post("/api/v1/programmes/{id}/requirement-blocks", programmeId)
                        .with(asRegistrar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Core\",\"kind\":\"CORE\",\"requiredCredits\":3,\"courseIds\":[\""
                                + courseId + "\"]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/programmes/{id}/requirement-blocks/publish", programmeId).with(asRegistrar()))
                .andExpect(status().isNoContent());
    }

    /** A persisted COURSE_SUBSTITUTION request — the FK target for {@code service_request_id}. */
    private UUID approvedRequest(UUID studentId) {
        ServiceRequest request = new ServiceRequest(
                studentId,
                ServiceRequestType.COURSE_SUBSTITUTION,
                "CS-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "{}",
                null,
                Instant.now().plus(10, ChronoUnit.DAYS));
        return serviceRequestRepository.saveAndFlush(request).getId();
    }

    @Test
    @DisplayName("lists a recorded substitution against a required course, with codes and student number resolved")
    void listsSubstitutionsForTheProgramme() throws Exception {
        var programme = academicFixtures.programme();
        Course required = academicFixtures.course(programme);
        Course substitute = academicFixtures.course(programme);
        Student student = academicFixtures.student(programme);

        createPublishedBlockWithCourse(programme.getId(), required.getId());

        substitutionRepository.saveAndFlush(new CourseSubstitution(
                student.getId(),
                required.getId(),
                substitute.getId(),
                approvedRequest(student.getId()),
                UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/programmes/{id}/course-substitutions", programme.getId()).with(asRegistrar()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentNumber").value(student.getStudentNumber()))
                .andExpect(jsonPath("$[0].requiredCourseCode").value(required.getCourseCode()))
                .andExpect(jsonPath("$[0].requiredCourseTitle").value(required.getTitle()))
                .andExpect(jsonPath("$[0].substituteCourseCode").value(substitute.getCourseCode()))
                .andExpect(jsonPath("$[0].substituteCourseTitle").value(substitute.getTitle()));
    }

    @Test
    @DisplayName("a substitution against a course the programme does not require is not listed")
    void ignoresSubstitutionsOutsideTheProgramme() throws Exception {
        var programme = academicFixtures.programme();
        Course required = academicFixtures.course(programme);
        Course unrelated = academicFixtures.course(programme);
        Course substitute = academicFixtures.course(programme);
        Student student = academicFixtures.student(programme);

        createPublishedBlockWithCourse(programme.getId(), required.getId());

        substitutionRepository.saveAndFlush(new CourseSubstitution(
                student.getId(),
                unrelated.getId(),
                substitute.getId(),
                approvedRequest(student.getId()),
                UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/programmes/{id}/course-substitutions", programme.getId()).with(asRegistrar()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a student may not read a programme's substitution list")
    void studentsAreForbidden() throws Exception {
        var programme = academicFixtures.programme();
        mockMvc.perform(get("/api/v1/programmes/{id}/course-substitutions", programme.getId()).with(asStudent()))
                .andExpect(status().isForbidden());
    }
}
