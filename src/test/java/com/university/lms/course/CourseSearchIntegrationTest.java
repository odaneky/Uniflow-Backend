package com.university.lms.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.course.domain.CourseStatus;
import com.university.lms.course.dto.CourseSummaryResponse;
import com.university.lms.course.service.CourseService;
import com.university.lms.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Covers catalog search against real PostgreSQL.
 *
 * <p>Written in response to a production failure: {@code GET /api/v1/courses} returned 500 with
 * {@code ERROR: function lower(bytea) does not exist}. The cause was a filter parameter that
 * Hibernate could not type — its first appearance in the JPQL was a bare {@code :search is null},
 * which carries no type information, so Hibernate fell back to its serializable mapping and bound
 * the value as {@code bytea}. PostgreSQL then had no {@code lower(bytea)} to call.
 *
 * <p>Nothing below a real database could have caught it. The mapping is valid JPQL, the unit tests
 * mock the repository, and the schema is correct — the defect only exists in the SQL Hibernate
 * emits and only surfaces when PostgreSQL resolves the function. Hence an integration test, and
 * hence the null case being asserted explicitly: a null search term is the default for every
 * unfiltered listing, so this path was the most-used one in the endpoint.
 */
class CourseSearchIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private com.university.lms.support.AcademicFixtures fixtures;

    /**
     * The exact shape of the request that failed in production: no filters at all.
     *
     * <p>Parameterised over null and blank because the service maps a blank term to null, so both
     * reach the repository as an absent filter.
     */
    @ParameterizedTest(name = "search = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("listing the catalog with no search term does not fail on parameter typing")
    void searchWithoutTermSucceeds(String search) {
        PageResponse<CourseSummaryResponse> page =
                courseService.search(null, null, search, PageRequest.of(0, 20, Sort.by("courseCode")));

        assertThat(page).isNotNull();
        assertThat(page.content()).isNotNull();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("a search term matches on title and on course code, case-insensitively")
    void searchMatchesTitleAndCode() {
        String unique = "Zzq" + UUID.randomUUID().toString().substring(0, 6);
        // Code must satisfy the DTO's ^[A-Z]{2,6}[0-9]{3,5}$ pattern and be unique across runs.
        String code = "ZZQ" + ThreadLocalRandom.current().nextInt(10000, 99999);
        var created = courseService.create(new com.university.lms.course.dto.CreateCourseRequest(
                code,
                unique + " Advanced Topics",
                "Created by CourseSearchIntegrationTest",
                3,
                3,
                fixtures.programme().getDepartment().getId(),
                java.util.Set.of(com.university.lms.course.domain.CourseComponent.LECTURE)));

        var byTitle = courseService.search(
                null, null, unique.toUpperCase(java.util.Locale.ROOT), PageRequest.of(0, 20, Sort.by("courseCode")));
        assertThat(byTitle.content())
                .as("title match must be case-insensitive")
                .extracting(CourseSummaryResponse::id)
                .contains(created.id());

        var byCode = courseService.search(
                null, null, created.courseCode().toLowerCase(java.util.Locale.ROOT),
                PageRequest.of(0, 20, Sort.by("courseCode")));
        assertThat(byCode.content())
                .as("course code match must be case-insensitive")
                .extracting(CourseSummaryResponse::id)
                .contains(created.id());
    }

    @Test
    @DisplayName("a term matching nothing returns an empty page rather than an error")
    void searchWithNoMatchesReturnsEmptyPage() {
        PageResponse<CourseSummaryResponse> page = courseService.search(
                null, null, "no-course-has-this-" + UUID.randomUUID(), PageRequest.of(0, 20, Sort.by("courseCode")));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    /**
     * A term containing LIKE metacharacters must be matched literally.
     *
     * <p>Without escaping, {@code %} is a wildcard: a student searching for "50%" would be shown
     * the entire catalog and reasonably conclude the search is broken. The assertion is written as
     * "matches the literal course, does not match the unrelated one" so it fails for the right
     * reason rather than merely counting rows.
     */
    @Test
    @DisplayName("LIKE wildcards typed by the user are matched literally, not as wildcards")
    void searchEscapesLikeWildcards() {
        UUID departmentId = fixtures.programme().getDepartment().getId();

        var withPercent = courseService.create(new com.university.lms.course.dto.CreateCourseRequest(
                "ZZP" + ThreadLocalRandom.current().nextInt(10000, 99999),
                "Statistics 50% Coursework",
                null,
                3,
                2,
                departmentId,
                java.util.Set.of(com.university.lms.course.domain.CourseComponent.LECTURE)));

        var withoutPercent = courseService.create(new com.university.lms.course.dto.CreateCourseRequest(
                "ZZU" + ThreadLocalRandom.current().nextInt(10000, 99999),
                "Statistics Full Coursework",
                null,
                3,
                2,
                departmentId,
                java.util.Set.of(com.university.lms.course.domain.CourseComponent.LECTURE)));

        var byLiteralPercent =
                courseService.search(null, null, "50%", PageRequest.of(0, 50, Sort.by("courseCode")));

        assertThat(byLiteralPercent.content())
                .extracting(CourseSummaryResponse::id)
                .as("the course whose title literally contains 50% must match")
                .contains(withPercent.id())
                .as("a course without that literal text must NOT match — '%' is not a wildcard here")
                .doesNotContain(withoutPercent.id());

        // '_' is the single-character wildcard and needs the same treatment.
        assertThat(courseService
                        .search(null, null, "Statistics_Full", PageRequest.of(0, 50, Sort.by("courseCode")))
                        .content())
                .as("'_' must not match the space in 'Statistics Full'")
                .extracting(CourseSummaryResponse::id)
                .doesNotContain(withoutPercent.id());
    }

    @Test
    @DisplayName("status and department filters combine with an absent search term")
    void filtersCombineWithNullSearch() {
        PageResponse<CourseSummaryResponse> page = courseService.search(
                CourseStatus.ACTIVE, null, null, PageRequest.of(0, 20, Sort.by("courseCode")));

        assertThat(page.content()).allSatisfy(c -> assertThat(c.status()).isEqualTo(CourseStatus.ACTIVE));
    }

}
