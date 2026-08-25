package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.academic.domain.AcademicTerm;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.course.dto.TermRolloverResponse;
import com.university.lms.course.repository.CourseRepository;
import com.university.lms.course.repository.CourseSectionRepository;
import com.university.lms.course.service.TermRolloverService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import com.university.lms.support.RunAs;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * D7: carrying a term's sections forward used to be entirely manual, section by section. Proves
 * rollover copies course/capacity/lecturer into the target term, skips a cancelled section, reports
 * without writing anything on a dry run, and does not double up a course that already made it to
 * the target term on an earlier, partial run.
 */
class TermRolloverIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TermRolloverService termRolloverService;

    @Autowired
    private AcademicFixtures academicFixtures;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    @DisplayName("copies an open section's course and capacity into the target term")
    void copiesAnOpenSection() throws Exception {
        AcademicTerm source = academicFixtures.openTerm();
        AcademicTerm target = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(source, 45);

        TermRolloverResponse response = RunAs.staff(
                () -> termRolloverService.rollover(source.getId(), target.getId(), false));

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.failed()).isZero();
        List<CourseSection> targetSections =
                courseSectionRepository.findByCourseIdAndAcademicTermId(section.getCourse().getId(), target.getId());
        assertThat(targetSections).hasSize(1);
        assertThat(targetSections.get(0).getCapacity()).isEqualTo(45);
    }

    @Test
    @DisplayName("skips a cancelled source section")
    void skipsCancelledSection() throws Exception {
        AcademicTerm source = academicFixtures.openTerm();
        AcademicTerm target = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(source, 30);
        section.cancel();
        courseSectionRepository.saveAndFlush(section);

        TermRolloverResponse response = RunAs.staff(
                () -> termRolloverService.rollover(source.getId(), target.getId(), false));

        assertThat(response.created()).isZero();
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(courseSectionRepository.findByCourseIdAndAcademicTermId(section.getCourse().getId(), target.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("a dry run reports what would happen without creating anything")
    void dryRunWritesNothing() throws Exception {
        AcademicTerm source = academicFixtures.openTerm();
        AcademicTerm target = academicFixtures.openTerm();
        CourseSection section = academicFixtures.openSection(source, 30);

        TermRolloverResponse response = RunAs.staff(
                () -> termRolloverService.rollover(source.getId(), target.getId(), true));

        assertThat(response.dryRun()).isTrue();
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.rows().get(0).outcome()).isEqualTo("WOULD_CREATE");
        assertThat(courseSectionRepository.findByCourseIdAndAcademicTermId(section.getCourse().getId(), target.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("a second run does not duplicate a course already rolled over")
    void secondRunDoesNotDuplicate() throws Exception {
        AcademicTerm source = academicFixtures.openTerm();
        AcademicTerm target = academicFixtures.openTerm();
        academicFixtures.openSection(source, 30);

        RunAs.staff(() -> termRolloverService.rollover(source.getId(), target.getId(), false));
        TermRolloverResponse second = RunAs.staff(
                () -> termRolloverService.rollover(source.getId(), target.getId(), false));

        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(second.rows().get(0).outcome()).isEqualTo("SKIPPED");
    }

    /**
     * The reason rollover runs outside its own transaction: each section's {@code addSection} call
     * gets its own, so a later section's failure cannot roll back an earlier section's success —
     * proved here, not just asserted in the javadoc.
     */
    @Test
    @DisplayName("one section's failure does not roll back sections already created earlier in the same run")
    void oneFailureDoesNotRollBackEarlierSuccesses() throws Exception {
        AcademicTerm source = academicFixtures.openTerm();
        AcademicTerm target = academicFixtures.openTerm();
        CourseSection willSucceed = academicFixtures.openSection(source, 30);
        CourseSection willFail = academicFixtures.openSection(source, 30);
        willFail.getCourse().retire();
        courseRepository.saveAndFlush(willFail.getCourse());

        TermRolloverResponse response = RunAs.staff(
                () -> termRolloverService.rollover(source.getId(), target.getId(), false));

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(courseSectionRepository.findByCourseIdAndAcademicTermId(
                        willSucceed.getCourse().getId(), target.getId()))
                .as("the section rolled over before the failing one must survive, not be rolled back with it")
                .hasSize(1);
    }
}
