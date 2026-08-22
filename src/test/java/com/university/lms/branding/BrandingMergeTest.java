package com.university.lms.branding;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.branding.config.BrandingProperties;
import com.university.lms.branding.domain.InstitutionBranding;
import com.university.lms.branding.dto.BrandingResponse;
import org.junit.jupiter.api.Test;

class BrandingMergeTest {

    private static final BrandingProperties DEFAULTS = new BrandingProperties(
            "UniFlow",
            "UNIFLOW",
            "UniFlow University",
            "Campus portal",
            "Choose how you sign in.",
            "Students",
            "Staff & faculty",
            "#171717",
            "#3b82f6",
            "Inter, sans-serif",
            "Inria Sans, sans-serif",
            "",
            "",
            "support@university.test",
            "info@university.test",
            "+1 876 000 0000");

    @Test
    void defaultsWhenNoOverrides() {
        InstitutionBranding row = new InstitutionBranding(InstitutionBranding.SINGLETON_ID);
        BrandingResponse effective = BrandingResponse.merge(DEFAULTS, row);
        assertThat(effective.productName()).isEqualTo("UniFlow");
        assertThat(effective.wordmark()).isEqualTo("UNIFLOW");
        assertThat(effective.contactEmail()).isEqualTo("info@university.test");
        assertThat(effective.source()).isEqualTo("defaults");
    }

    @Test
    void databaseFieldsOverrideDefaults() {
        InstitutionBranding row = new InstitutionBranding(InstitutionBranding.SINGLETON_ID);
        row.replace(
                "UWI Portal",
                "UWI",
                "The University of the West Indies",
                "Welcome",
                "Sign in to continue.",
                "Students",
                "Staff",
                "#003366",
                "#F4A300",
                null,
                null,
                "https://cdn.example/logo.svg",
                "https://cdn.example/favicon.ico",
                "help@uwi.edu",
                "contact@uwi.edu",
                "+1 876 123 4567");
        BrandingResponse effective = BrandingResponse.merge(DEFAULTS, row);
        assertThat(effective.productName()).isEqualTo("UWI Portal");
        assertThat(effective.wordmark()).isEqualTo("UWI");
        assertThat(effective.primaryColor()).isEqualTo("#003366");
        assertThat(effective.logoUrl()).isEqualTo("https://cdn.example/logo.svg");
        assertThat(effective.fontSans()).isEqualTo("Inter, sans-serif");
        assertThat(effective.phoneNumber()).isEqualTo("+1 876 123 4567");
        assertThat(effective.source()).isEqualTo("database");
    }
}
