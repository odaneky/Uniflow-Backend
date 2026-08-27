package com.university.lms.finance.repository;

import com.university.lms.finance.domain.InvoiceLineItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, UUID> {

    List<InvoiceLineItem> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
