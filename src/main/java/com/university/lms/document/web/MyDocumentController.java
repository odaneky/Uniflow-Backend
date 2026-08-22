package com.university.lms.document.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.document.dto.DocumentResponse;
import com.university.lms.document.service.DocumentService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
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
}
