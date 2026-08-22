package com.university.lms.document.web;

import com.university.lms.document.dto.CreateDocumentRequest;
import com.university.lms.document.dto.DocumentResponse;
import com.university.lms.document.service.DocumentService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> register(@Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse created = documentService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + created.id())).body(created);
    }
}
