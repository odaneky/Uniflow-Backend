package com.university.lms.finance.web;

import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import com.university.lms.finance.dto.InvoiceResponse;
import com.university.lms.finance.dto.IssueInvoiceRequest;
import com.university.lms.finance.dto.VoidInvoiceRequest;
import com.university.lms.finance.service.InvoiceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E6: billable documents — a term's charges, frozen and addressable to the student or a sponsor. */
@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/accounts/{studentId}/invoices")
    public List<InvoiceResponse> forStudent(@PathVariable UUID studentId) {
        return invoiceService.forStudent(studentId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/accounts/{studentId}/invoices")
    public ResponseEntity<InvoiceResponse> issue(
            @PathVariable UUID studentId, @Valid @RequestBody IssueInvoiceRequest request) {
        InvoiceResponse created = invoiceService.issue(studentId, request);
        return ResponseEntity.created(URI.create("/api/v1/invoices/" + created.id())).body(created);
    }

    @AccessClass(REGISTRY_ONLY)
    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse find(@PathVariable UUID invoiceId) {
        return invoiceService.find(invoiceId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/invoices/{invoiceId}/mark-paid")
    public InvoiceResponse markPaid(@PathVariable UUID invoiceId) {
        return invoiceService.markPaid(invoiceId);
    }

    @AccessClass(REGISTRY_ONLY)
    @PostMapping("/invoices/{invoiceId}/void")
    public InvoiceResponse voidInvoice(
            @PathVariable UUID invoiceId, @Valid @RequestBody VoidInvoiceRequest request) {
        return invoiceService.voidInvoice(invoiceId, request.reason());
    }
}
