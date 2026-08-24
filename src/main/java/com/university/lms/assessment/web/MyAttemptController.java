package com.university.lms.assessment.web;

import com.university.lms.assessment.dto.AttemptResponse;
import com.university.lms.assessment.service.AssessmentService;
import com.university.lms.assessment.service.AssessmentService.StoredAttemptFile;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The caller's own attempts at assessed work. */
@RestController
@RequestMapping("/api/v1/me/assessments")
public class MyAttemptController {

    private final AssessmentService assessmentService;

    public MyAttemptController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/{assessmentId}/attempts")
    public List<AttemptResponse> attempts(@PathVariable UUID assessmentId) {
        return assessmentService.ownAttempts(assessmentId);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @PostMapping(value = "/{assessmentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttemptResponse submit(
            @PathVariable UUID assessmentId, @RequestParam("file") MultipartFile file) {
        return assessmentService.submitOwn(assessmentId, file);
    }

    @AccessClass(OWN_RECORD_ONLY)
    @GetMapping("/{assessmentId}/attempts/{attemptId}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID assessmentId, @PathVariable UUID attemptId) {
        return file(assessmentService.downloadOwn(assessmentId, attemptId));
    }

    static ResponseEntity<byte[]> file(StoredAttemptFile stored) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(stored.contentType());
        } catch (Exception ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(stored.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(type)
                .body(stored.content());
    }
}
