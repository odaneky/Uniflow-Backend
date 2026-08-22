package com.university.lms.document.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.BusinessException;
import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.exception.ResourceAlreadyExistsException;
import com.university.lms.document.api.DocumentStore;
import com.university.lms.document.api.DocumentStore.StoredFile;
import com.university.lms.document.config.StorageProperties;
import com.university.lms.document.domain.Document;
import com.university.lms.document.domain.DocumentErrorCode;
import com.university.lms.document.domain.DocumentType;
import com.university.lms.document.domain.StorageProvider;
import com.university.lms.document.dto.CreateDocumentRequest;
import com.university.lms.document.dto.DocumentResponse;
import com.university.lms.document.repository.DocumentRepository;
import com.university.lms.document.storage.LocalFilesystemBlobStore;
import com.university.lms.identity.api.CurrentUserProvider;
import com.university.lms.identity.api.UserDirectory;
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
    private final LocalFilesystemBlobStore blobStore;
    private final StorageProperties storageProperties;

    public DocumentService(
            DocumentRepository documentRepository,
            CurrentUserProvider currentUserProvider,
            UserDirectory userDirectory,
            LocalFilesystemBlobStore blobStore,
            StorageProperties storageProperties) {
        this.documentRepository = documentRepository;
        this.currentUserProvider = currentUserProvider;
        this.userDirectory = userDirectory;
        this.blobStore = blobStore;
        this.storageProperties = storageProperties;
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
        if (!currentUserProvider.require().isStaff()) {
            return Optional.empty();
        }
        return content(documentId);
    }

    public Optional<DocumentResponse> findMetadata(UUID documentId) {
        if (!currentUserProvider.require().isStaff()) {
            return Optional.empty();
        }
        return documentRepository.findById(documentId).map(DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse register(CreateDocumentRequest request) {
        if (!currentUserProvider.require().isStaff()) {
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
        Document document = new Document(
                type, safeName, contentType, content.length, key, StorageProvider.LOCAL_FILESYSTEM, ownerUserId);
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
