package com.university.lms.admissions.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned <b>once</b>, when an application is created.
 *
 * <p>The only time the raw token exists outside the applicant's own client: the database keeps only
 * its hash, so it cannot be shown again. Losing it is recoverable through
 * {@code POST /applications/resume}, which issues a fresh one by email.
 *
 * <p>Deliberately a separate type from {@code ApplicationResponse}. If the token were a field on the
 * ordinary response it would be returned by every read, and would end up in logs, caches and staff
 * screens — which is exactly the mistake this whole change exists to undo.
 */
public record ApplicationAccessResponse(
        ApplicationResponse application, String accessToken, Instant accessTokenExpiresAt) {}
