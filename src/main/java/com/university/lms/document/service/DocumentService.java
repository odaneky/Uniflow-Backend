package com.university.lms.document.service;

import com.university.lms.academic.api.AcademicStructure;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.common.security.SecurityRoles;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.document.api.DocumentStore.StoredFile;
import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.DocumentErrorCode;
import com.university.lms.document.domain.DocumentType;
import com.university.lms.document.dto.CreateDocumentRequest;
import com.university.lms.document.dto.DocumentResponse;
import com.university.lms.document.repository.DocumentRepository;
import com.university.lms.document.storage.BlobStore;
import com.university.lms.identity.api.CurrentUser;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.staffing.api.StaffAppointments;
import com.university.lms.student.api.StudentDirectory;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DocumentService implements DocumentStore {

    private final DocumentRepository documentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserDirectory userDirectory;
    private final BlobStore blobStore;
    private final StorageProperties storageProperties;
    private final StaffAppointments staffAppointments;
    private final StudentDirectory studentDirectory;
    private final AcademicStructure academicStructure;

    public DocumentService(
            DocumentRepository documentRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectory userDirectory,
            BlobStore blobStore,
            StorageProperties storageProperties,
            StaffAppointments staffAppointments,
            StudentDirectory studentDirectory,
            AcademicStructure academicStructure) {
        this.documentRepository = documentRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
        this.blobStore = blobStore;
        this.storageProperties = storageProperties;
        this.staffAppointments = staffAppointments;
        this.studentDirectory = studentDirectory;
        this.academicStructure = academicStructure;
    }

    public PageResponse<DocumentResponse> own(Pageable pageable) {
        return PageResponse.from(
                documentRepository.findByOwnerUserId(currentUserProvider.require().userId(), pageable),
                DocumentResponse::from);
    }

    public Optional<DocumentResponse> findOwn(UUID documentId) {
        return documentRepository
                .findById(documentId)
                .filter(doc -> doc.getOwnerUserId().equals(currentUserProvider.require().userId()))
                .map(DocumentResponse::from);
    }

    public Optional<byte[]> downloadOwn(UUID documentId) {
        return findOwn(documentId).flatMap(ignored -> content(documentId));
    }

    public Optional<byte[]> downloadForStaff(UUID documentId) {
        CurrentUser caller = currentUserProvider.require();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || !isAuthorizedStaff(caller, document.getOwnerUserId())) {
            return Optional.empty();
        }
        return content(documentId);
    }

    public Optional<DocumentResponse> findMetadata(UUID documentId) {
        CurrentUser caller = currentUserProvider.require();
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || !isAuthorizedStaff(caller, document.getOwnerUserId())) {
            return Optional.empty();
        }
        return Optional.of(DocumentResponse.from(document));
    }

    /**
     * A5: the same org-scoped, fail-open check as {@code LearningService.isAuthorizedStaff}, but
     * resolved through the document's owner rather than a course section — a document has no
     * course or department of its own. Owner &rarr; student record &rarr; programme &rarr;
     * department; fails open (returns {@code true}, {@code isStaff()}'s old behaviour) whenever any
     * step is unresolvable, which includes the ordinary case of an owner who is not a student at
     * all (a staff member's own document, for instance) — that is not a data gap to route around,
     * it is simply a document with no department to scope by.
     */
    private boolean isAuthorizedStaff(CurrentUser caller, UUID ownerUserId) {
        if (!caller.isStaff()) {
            return false;
        }
        if (caller.hasRole(SecurityRoles.SYSTEM_ADMIN)) {
            return true;
        }
        if (staffAppointments.activeAppointmentsOf(caller.userId()).isEmpty()) {
            return true;
        }
        Optional<UUID> orgUnitId = studentDirectory
                .studentIdOfUser(ownerUserId)
                .flatMap(studentDirectory::findById)
                .map(StudentDirectory.StudentSummary::programmeId)
                .flatMap(academicStructure::departmentOfProgramme)
                .flatMap(departmentId -> staffAppointments.orgUnitFor("DEPARTMENT", departmentId));
        return orgUnitId.isEmpty() || staffAppointments.isAppointedOver(caller.userId(), orgUnitId.get());
    }

    /**
     * A5: any staff role could register a document against any student's record, regardless of
     * department — the same over-reach as {@link #downloadForStaff} and {@link #findMetadata},
     * just on the write side. Reuses {@link #isAuthorizedStaff} rather than duplicating its
     * resolution and fail-open behaviour.
     */
    @Transactional
    public DocumentResponse register(CreateDocumentRequest request) {
        CurrentUser caller = currentUserProvider.require();
        if (!isAuthorizedStaff(caller, request.ownerUserId())) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        if (!userDirectory.exists(request.ownerUserId())) {
            throw new ForbiddenException(
                    CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
        }
        if (documentRepository.findByStorageKey(request.storageKey()).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    DocumentErrorCode.DOCUMENT_STORAGE_KEY_EXISTS, "A document already uses that storage key");
        }
        Document document = new Document(
                request.documentType(),
                request.fileName(),
                request.contentType(),
                request.sizeBytes(),
                request.storageKey(),
                request.storageProvider(),
                request.ownerUserId());
        try {
            return DocumentResponse.from(documentRepository.saveAndFlush(document));
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceAlreadyExistsException(
                    DocumentErrorCode.DOCUMENT_STORAGE_KEY_EXISTS, "A document already uses that storage key", ex);
        }
    }

    @Override
    @Transactional
    public StoredFile store(
            UUID ownerUserId, String documentType, String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND, "File is required");
        }
        long max = storageProperties.maxUploadBytes() <= 0 ? 12_582_912L : storageProperties.maxUploadBytes();
        if (content.length > max) {
            throw new BusinessException(
                    DocumentErrorCode.DOCUMENT_TOO_LARGE, "File must be at most " + max + " bytes");
        }
        DocumentType type = DocumentType.valueOf(documentType);
        String safeName = safeFileName(fileName);
        String key = "uploads/" + ownerUserId + "/" + UUID.randomUUID();
        blobStore.put(key, content);
        Document document =
                new Document(type, safeName, contentType, content.length, key, blobStore.provider(), ownerUserId);
        document.recordChecksum(sha256(content));
        Document saved = documentRepository.saveAndFlush(document);
        return toStored(saved);
    }

    @Override
    public Optional<StoredFile> find(UUID documentId) {
        return documentRepository.findById(documentId).map(DocumentService::toStored);
    }

    @Override
    public Optional<byte[]> content(UUID documentId) {
        return documentRepository.findById(documentId).map(document -> blobStore.get(document.getStorageKey()));
    }

    private static StoredFile toStored(Document document) {
        return new StoredFile(
                document.getId(),
                document.getOwnerUserId(),
                document.getDocumentType().name(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes());
    }

    private static String safeFileName(String fileName) {
        String raw = fileName == null || fileName.isBlank() ? "upload.bin" : fileName;
        String name = Paths.get(raw).getFileName().toString();
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "upload.bin" : cleaned;
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }
}
