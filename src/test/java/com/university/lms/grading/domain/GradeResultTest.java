package com.university.lms.grading.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GradeResultTest {

    @Test
    void lettersMapToPassOrFail() {
        assertThat(GradeResult.fromLetter("A")).isEqualTo(GradeResult.PASS);
        assertThat(GradeResult.fromLetter("B+")).isEqualTo(GradeResult.PASS);
        assertThat(GradeResult.fromLetter("P")).isEqualTo(GradeResult.PASS);
        assertThat(GradeResult.fromLetter("F")).isEqualTo(GradeResult.FAIL);
        assertThat(GradeResult.fromLetter("E")).isEqualTo(GradeResult.FAIL);
        assertThat(GradeResult.fromLetter("U")).isEqualTo(GradeResult.FAIL);
        assertThat(GradeResult.fromLetter("N")).isEqualTo(GradeResult.FAIL);
        assertThat(GradeResult.fromLetter("")).isEqualTo(GradeResult.FAIL);
        assertThat(GradeResult.fromLetter(null)).isEqualTo(GradeResult.FAIL);
    }
}
