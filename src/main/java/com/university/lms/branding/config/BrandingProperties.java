package com.university.lms.branding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deploy-time branding defaults ({@code LMS_*} / {@code lms.branding.*}).
 *
 * <p>Admin overrides live in {@code institution_branding}. The SPA reads the merged effective
 * branding at runtime. The Keycloak login theme is file-based and follows these same defaults at
 * deploy time — it does not hot-reload when an admin saves in System settings.
 */
@ConfigurationProperties("lms.branding")
public record BrandingProperties(
        String productName,
        String wordmark,
        String institutionName,
        String welcomeTitle,
        String welcomeSubtitle,
        String studentCtaLabel,
        String staffCtaLabel,
        String primaryColor,
        String accentColor,
        String fontSans,
        String fontDisplay,
        String logoUrl,
        String faviconUrl,
        String supportEmail,
        String contactEmail,
        String phoneNumber) {}
