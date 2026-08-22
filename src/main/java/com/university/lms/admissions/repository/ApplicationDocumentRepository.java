package com.university.lms.admissions.repository;

import com.university.lms.admissions.domain.ApplicationDocument;
import com.university.lms.admissions.domain.ApplicationDocument.ApplicationDocumentId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, ApplicationDocumentId> {

    List<ApplicationDocument> findByApplicationId(UUID applicationId);

    boolean existsByApplicationIdAndDocumentId(UUID applicationId, UUID documentId);
}
