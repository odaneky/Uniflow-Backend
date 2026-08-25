package com.university.lms.curriculum.domain;

/**
 * Latin honours awarded at conferral, by cumulative GPA at the moment of graduation. A
 * conventional starting default — 3.90/3.70/3.50 — not an institution-specific policy read from
 * configuration, the same starting point {@code DefaultStudentBilling}'s original refund taper
 * was before {@code RefundPolicy} existed; there is nowhere yet for a registrar to set the
 * institution's own bands. Not awarded on a certificate programme, which has no GPA floor to
 * measure against in the first place.
 */
public enum Honours {
    CUM_LAUDE,
    MAGNA_CUM_LAUDE,
    SUMMA_CUM_LAUDE
}
