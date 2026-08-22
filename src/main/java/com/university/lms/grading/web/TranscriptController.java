package com.university.lms.grading.web;

import com.university.lms.grading.service.TranscriptService;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TranscriptController {

    private final TranscriptService transcriptService;

    public TranscriptController(TranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @GetMapping(value = "/students/{studentId}/transcript.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> studentTranscript(@PathVariable UUID studentId) {
        return pdfResponse(transcriptService.officialTranscript(studentId));
    }

    @GetMapping(value = "/me/transcript/official", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> ownTranscript() {
        return pdfResponse(transcriptService.ownOfficialTranscript());
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] pdf) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transcript.pdf\"")
                .body(pdf);
    }
}
