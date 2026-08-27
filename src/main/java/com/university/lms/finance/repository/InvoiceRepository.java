package com.university.lms.finance.repository;

import com.university.lms.finance.domain.Invoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByStudentIdOrderByIssuedAtDesc(UUID studentId);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
