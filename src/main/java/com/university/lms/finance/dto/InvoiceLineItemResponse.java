package com.university.lms.finance.dto;

import com.university.lms.finance.domain.InvoiceLineItem;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineItemResponse(UUID id, String description, BigDecimal amount) {

    public static InvoiceLineItemResponse from(InvoiceLineItem item) {
        return new InvoiceLineItemResponse(item.getId(), item.getDescription(), item.getAmount());
    }
}
