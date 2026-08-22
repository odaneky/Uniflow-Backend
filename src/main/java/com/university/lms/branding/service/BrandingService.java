package com.university.lms.branding.service;

import com.university.lms.branding.config.BrandingProperties;
import com.university.lms.branding.domain.InstitutionBranding;
import com.university.lms.branding.dto.BrandingResponse;
import com.university.lms.branding.dto.ReplaceBrandingRequest;
import com.university.lms.branding.repository.InstitutionBrandingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandingService {

    private final InstitutionBrandingRepository brandingRepository;
    private final BrandingProperties defaults;

    public BrandingService(InstitutionBrandingRepository brandingRepository, BrandingProperties defaults) {
        this.brandingRepository = brandingRepository;
        this.defaults = defaults;
    }

    @Transactional(readOnly = true)
    public BrandingResponse effective() {
        return BrandingResponse.merge(defaults, requireRow());
    }

    @Transactional
    public BrandingResponse replace(ReplaceBrandingRequest request) {
        InstitutionBranding row = requireRow();
        row.replace(
                request.productName(),
                request.wordmark(),
                request.institutionName(),
                request.welcomeTitle(),
                request.welcomeSubtitle(),
                request.studentCtaLabel(),
                request.staffCtaLabel(),
                request.primaryColor(),
                request.accentColor(),
                request.fontSans(),
                request.fontDisplay(),
                request.logoUrl(),
                request.faviconUrl(),
                request.supportEmail(),
                request.contactEmail(),
                request.phoneNumber());
        return BrandingResponse.merge(defaults, row);
    }

    @Transactional
    public BrandingResponse resetToDefaults() {
        InstitutionBranding row = requireRow();
        row.clearOverrides();
        return BrandingResponse.merge(defaults, row);
    }

    private InstitutionBranding requireRow() {
        return brandingRepository
                .findById(InstitutionBranding.SINGLETON_ID)
                .orElseGet(() -> brandingRepository.save(new InstitutionBranding(InstitutionBranding.SINGLETON_ID)));
    }
}
