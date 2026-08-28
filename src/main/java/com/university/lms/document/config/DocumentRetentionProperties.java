package com.university.lms.document.config;

import com.university.lms.document.domain.DocumentType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long a document's bytes are kept before the retention sweeper purges them, by {@link
 * DocumentType}. Identification documents (a driver's licence, a passport scan) get a shorter,
 * dedicated window; everything else purgeable shares {@code otherDays}. {@code COURSE_MATERIAL},
 * {@code ASSESSMENT_SUBMISSION}, {@code TRANSCRIPT} and {@code CERTIFICATE} are academic-record
 * evidence with no purge date — {@link #expiryFor} returns {@code null} for them, meaning "never".
 */
@ConfigurationProperties("lms.documents.retention")
public record DocumentRetentionProperties(
        @DefaultValue("365") long identificationDays, @DefaultValue("730") long otherDays) {

    public Instant expiryFor(DocumentType documentType, Instant from) {
        return switch (documentType) {
            case IDENTIFICATION -> from.plus(identificationDays, ChronoUnit.DAYS);
            case OTHER -> from.plus(otherDays, ChronoUnit.DAYS);
            case COURSE_MATERIAL, ASSESSMENT_SUBMISSION, TRANSCRIPT, CERTIFICATE -> null;
        };
    }
}
