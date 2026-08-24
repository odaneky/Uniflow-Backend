package com.university.lms.branding.web;

import com.university.lms.branding.dto.BrandingResponse;
import com.university.lms.branding.dto.ReplaceBrandingRequest;
import com.university.lms.branding.service.BrandingService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campus branding. {@code GET} is public so the welcome page can theme before sign-in.
 * Writes are {@code SYSTEM_ADMIN} only.
 */
@RestController
@RequestMapping("/api/v1/branding")
public class BrandingController {

    private final BrandingService brandingService;

    public BrandingController(BrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @AccessClass(PUBLIC)
    @GetMapping
    public BrandingResponse find() {
        return brandingService.effective();
    }

    @AccessClass(REGISTRY_ONLY)
    @PutMapping
    public BrandingResponse replace(@Valid @RequestBody ReplaceBrandingRequest request) {
        return brandingService.replace(request);
    }

    @AccessClass(REGISTRY_ONLY)
    @DeleteMapping
    public BrandingResponse reset() {
        return brandingService.resetToDefaults();
    }
}
