package com.university.lms.grading.domain;

/** Pass / fail outcome derived from a published letter grade. */
public enum GradeResult {
    PASS,
    FAIL;

    /**
     * Letters in {@code F}, {@code E}, {@code U}, {@code N} (and blank) are fails. Everything else
     * with a letter is a pass — including P for pass/fail modules.
     */
    public static GradeResult fromLetter(String letter) {
        if (letter == null || letter.isBlank()) {
            return FAIL;
        }
        String normalized = letter.trim().toUpperCase();
        char head = normalized.charAt(0);
        if (head == 'F' || head == 'E' || head == 'U' || head == 'N') {
            return FAIL;
        }
        return PASS;
    }

    public boolean isPass() {
        return this == PASS;
    }
}
