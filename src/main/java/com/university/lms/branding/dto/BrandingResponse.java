package com.university.lms.branding.dto;

import com.university.lms.branding.config.BrandingProperties;
import com.university.lms.branding.domain.InstitutionBranding;

/**
 * Effective campus branding (deploy defaults merged with admin overrides).
 *
 * <p>Future email/PDF renderers should take this record, not hard-code UniFlow.
 */
public record BrandingResponse(
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
        String phoneNumber,
        String source) {

    public static BrandingResponse fromDefaults(BrandingProperties defaults) {
        return new BrandingResponse(
                defaults.productName(),
                defaults.wordmark(),
                defaults.institutionName(),
                defaults.welcomeTitle(),
                defaults.welcomeSubtitle(),
                defaults.studentCtaLabel(),
                defaults.staffCtaLabel(),
                defaults.primaryColor(),
                defaults.accentColor(),
                defaults.fontSans(),
                defaults.fontDisplay(),
                nullIfBlank(defaults.logoUrl()),
                nullIfBlank(defaults.faviconUrl()),
                nullIfBlank(defaults.supportEmail()),
                nullIfBlank(defaults.contactEmail()),
                nullIfBlank(defaults.phoneNumber()),
                "defaults");
    }

    public static BrandingResponse merge(BrandingProperties defaults, InstitutionBranding override) {
        boolean any = hasAnyOverride(override);
        return new BrandingResponse(
                pick(override.getProductName(), defaults.productName()),
                pick(override.getWordmark(), defaults.wordmark()),
                pick(override.getInstitutionName(), defaults.institutionName()),
                pick(override.getWelcomeTitle(), defaults.welcomeTitle()),
                pick(override.getWelcomeSubtitle(), defaults.welcomeSubtitle()),
                pick(override.getStudentCtaLabel(), defaults.studentCtaLabel()),
                pick(override.getStaffCtaLabel(), defaults.staffCtaLabel()),
                pick(override.getPrimaryColor(), defaults.primaryColor()),
                pick(override.getAccentColor(), defaults.accentColor()),
                pick(override.getFontSans(), defaults.fontSans()),
                pick(override.getFontDisplay(), defaults.fontDisplay()),
                pick(override.getLogoUrl(), nullIfBlank(defaults.logoUrl())),
                pick(override.getFaviconUrl(), nullIfBlank(defaults.faviconUrl())),
                pick(override.getSupportEmail(), nullIfBlank(defaults.supportEmail())),
                pick(override.getContactEmail(), nullIfBlank(defaults.contactEmail())),
                pick(override.getPhoneNumber(), nullIfBlank(defaults.phoneNumber())),
                any ? "database" : "defaults");
    }

    private static boolean hasAnyOverride(InstitutionBranding override) {
        return override.getProductName() != null
                || override.getWordmark() != null
                || override.getInstitutionName() != null
                || override.getWelcomeTitle() != null
                || override.getWelcomeSubtitle() != null
                || override.getStudentCtaLabel() != null
                || override.getStaffCtaLabel() != null
                || override.getPrimaryColor() != null
                || override.getAccentColor() != null
                || override.getFontSans() != null
                || override.getFontDisplay() != null
                || override.getLogoUrl() != null
                || override.getFaviconUrl() != null
                || override.getSupportEmail() != null
                || override.getContactEmail() != null
                || override.getPhoneNumber() != null;
    }

    private static String pick(String override, String fallback) {
        return override != null ? override : fallback;
    }

    private static String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
