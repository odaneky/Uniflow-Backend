package com.university.lms.branding.web;

import com.university.lms.branding.dto.BrandingResponse;
import com.university.lms.branding.dto.ReplaceBrandingRequest;
import com.university.lms.branding.service.BrandingService;
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

    @GetMapping
    public BrandingResponse find() {
        return brandingService.effective();
    }

    @PutMapping
    public BrandingResponse replace(@Valid @RequestBody ReplaceBrandingRequest request) {
        return brandingService.replace(request);
    }

    @DeleteMapping
    public BrandingResponse reset() {
        return brandingService.resetToDefaults();
    }
}
