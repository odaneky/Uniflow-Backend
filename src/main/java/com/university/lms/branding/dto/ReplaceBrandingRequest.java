package com.university.lms.branding.dto;

import jakarta.validation.constraints.Size;

/** Body for {@code PUT /api/v1/branding}. Blank fields clear that override. */
public record ReplaceBrandingRequest(
        @Size(max = 80) String productName,
        @Size(max = 80) String wordmark,
        @Size(max = 200) String institutionName,
        @Size(max = 120) String welcomeTitle,
        @Size(max = 400) String welcomeSubtitle,
        @Size(max = 80) String studentCtaLabel,
        @Size(max = 80) String staffCtaLabel,
        @Size(max = 32) String primaryColor,
        @Size(max = 32) String accentColor,
        @Size(max = 120) String fontSans,
        @Size(max = 120) String fontDisplay,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String faviconUrl,
        @Size(max = 254) String supportEmail,
        @Size(max = 254) String contactEmail,
        @Size(max = 40) String phoneNumber) {}
