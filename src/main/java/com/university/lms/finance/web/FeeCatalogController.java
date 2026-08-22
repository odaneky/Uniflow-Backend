package com.university.lms.finance.web;

import com.university.lms.finance.dto.CreateFeeRequest;
import com.university.lms.finance.dto.FeeResponse;
import com.university.lms.finance.dto.UpdateFeeRequest;
import com.university.lms.finance.service.FeeCatalogService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** University fee catalog. Amounts posted at enrolment; later edits do not rewrite the ledger. */
@RestController
@RequestMapping("/api/v1/fee-catalog")
public class FeeCatalogController {

    private final FeeCatalogService feeCatalogService;

    public FeeCatalogController(FeeCatalogService feeCatalogService) {
        this.feeCatalogService = feeCatalogService;
    }

    @GetMapping
    public List<FeeResponse> findAll() {
        return feeCatalogService.findAll();
    }

    @PostMapping
    public ResponseEntity<FeeResponse> create(@Valid @RequestBody CreateFeeRequest request) {
        FeeResponse created = feeCatalogService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/fee-catalog/" + created.id())).body(created);
    }

    @PatchMapping("/{feeId}")
    public FeeResponse update(@PathVariable UUID feeId, @Valid @RequestBody UpdateFeeRequest request) {
        return feeCatalogService.update(feeId, request);
    }

    @DeleteMapping("/{feeId}")
    public void deactivate(@PathVariable UUID feeId) {
        feeCatalogService.deactivate(feeId);
    }
}
