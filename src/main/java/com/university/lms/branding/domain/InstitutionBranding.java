package com.university.lms.branding.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** One row of optional campus branding overrides. Null field = keep deploy default. */
@Entity
@Table(name = "institution_branding")
@Getter
public class InstitutionBranding extends BaseEntity {

    public static final UUID SINGLETON_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-000000000003");

    @Column(name = "product_name", length = 80)
    private String productName;

    @Column(name = "wordmark", length = 80)
    private String wordmark;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "welcome_title", length = 120)
    private String welcomeTitle;

    @Column(name = "welcome_subtitle", length = 400)
    private String welcomeSubtitle;

    @Column(name = "student_cta_label", length = 80)
    private String studentCtaLabel;

    @Column(name = "staff_cta_label", length = 80)
    private String staffCtaLabel;

    @Column(name = "primary_color", length = 32)
    private String primaryColor;

    @Column(name = "accent_color", length = 32)
    private String accentColor;

    @Column(name = "font_sans", length = 120)
    private String fontSans;

    @Column(name = "font_display", length = 120)
    private String fontDisplay;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "support_email", length = 254)
    private String supportEmail;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    protected InstitutionBranding() {}

    public InstitutionBranding(UUID id) {
        setId(id);
    }

    public void replace(
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
            String phoneNumber) {
        this.productName = blankToNull(productName);
        this.wordmark = blankToNull(wordmark);
        this.institutionName = blankToNull(institutionName);
        this.welcomeTitle = blankToNull(welcomeTitle);
        this.welcomeSubtitle = blankToNull(welcomeSubtitle);
        this.studentCtaLabel = blankToNull(studentCtaLabel);
        this.staffCtaLabel = blankToNull(staffCtaLabel);
        this.primaryColor = blankToNull(primaryColor);
        this.accentColor = blankToNull(accentColor);
        this.fontSans = blankToNull(fontSans);
        this.fontDisplay = blankToNull(fontDisplay);
        this.logoUrl = blankToNull(logoUrl);
        this.faviconUrl = blankToNull(faviconUrl);
        this.supportEmail = blankToNull(supportEmail);
        this.contactEmail = blankToNull(contactEmail);
        this.phoneNumber = blankToNull(phoneNumber);
    }

    public void clearOverrides() {
        replace(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
