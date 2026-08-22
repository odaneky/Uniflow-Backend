package com.university.lms.document.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.document.domain.DocumentErrorCode;
import com.university.lms.document.dto.DocumentResponse;
import com.university.lms.document.service.DocumentService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MyDocumentController {

    private final DocumentService documentService;

    public MyDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/documents")
    public PageResponse<DocumentResponse> documents(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.own(pageable);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var meta = documentService
                .findOwn(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        DocumentErrorCode.DOCUMENT_NOT_FOUND, "No document exists with id " + id));
        byte[] bytes = documentService
                .downloadOwn(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        DocumentErrorCode.DOCUMENT_NOT_FOUND, "Document content is unavailable"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.fileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        meta.contentType() != null ? meta.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .body(bytes);
    }
}
