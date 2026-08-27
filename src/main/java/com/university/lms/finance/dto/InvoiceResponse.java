package com.university.lms.finance.dto;

import com.university.lms.finance.domain.Invoice;
import com.university.lms.finance.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID studentId,
        UUID academicTermId,
        String invoiceNumber,
        InvoiceStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant issuedAt,
        LocalDate dueOn,
        String billToName,
        String billToEmail,
        String notes,
        Instant paidAt,
        Instant voidedAt,
        String voidReason,
        long daysOverdue,
        boolean overdue,
        List<InvoiceLineItemResponse> lineItems) {

    public static InvoiceResponse from(Invoice invoice, List<InvoiceLineItemResponse> lineItems, LocalDate asOf) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getStudentId(),
                invoice.getAcademicTermId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getIssuedAt(),
                invoice.getDueOn(),
                invoice.getBillToName(),
                invoice.getBillToEmail(),
                invoice.getNotes(),
                invoice.getPaidAt(),
                invoice.getVoidedAt(),
                invoice.getVoidReason(),
                invoice.daysOverdue(asOf),
                invoice.isOverdue(asOf),
                lineItems);
    }
}
