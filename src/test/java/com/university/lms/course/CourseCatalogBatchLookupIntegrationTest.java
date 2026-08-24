package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.course.api.CourseCatalog;
import com.university.lms.course.domain.CourseSection;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import com.university.lms.support.AcademicFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * F6: checking a student's prerequisite history used to call {@code findSection}/{@code
 * findCourse} once per historical enrolment — two queries per row. These batch forms are what let
 * the caller fetch everything it needs in two queries total, regardless of history length.
 */
class CourseCatalogBatchLookupIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseCatalog courseCatalog;

    @Autowired
    private AcademicFixtures fixtures;

    @Test
    @DisplayName("findSections and findCourses return every requested row in one call each")
    void batchLookupsReturnEveryRequestedRow() {
        var term = fixtures.openTerm();
        CourseSection sectionA = fixtures.openSection(term, 30);
        CourseSection sectionB = fixtures.openSection(term, 30);

        List<CourseCatalog.SectionSummary> sections =
                courseCatalog.findSections(List.of(sectionA.getId(), sectionB.getId()));
        assertThat(sections).hasSize(2).extracting(CourseCatalog.SectionSummary::id)
                .containsExactlyInAnyOrder(sectionA.getId(), sectionB.getId());

        List<CourseCatalog.CourseSummary> courses = courseCatalog.findCourses(
                List.of(sectionA.getCourse().getId(), sectionB.getCourse().getId()));
        assertThat(courses).hasSize(2).extracting(CourseCatalog.CourseSummary::id)
                .containsExactlyInAnyOrder(sectionA.getCourse().getId(), sectionB.getCourse().getId());
    }

    @Test
    @DisplayName("an empty id collection returns an empty list rather than every row")
    void emptyIdsReturnNothing() {
        assertThat(courseCatalog.findSections(List.of())).isEmpty();
        assertThat(courseCatalog.findCourses(List.of())).isEmpty();
    }
}
