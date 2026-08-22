package com.university.lms.communication.web;

import com.university.lms.communication.dto.AnnouncementResponse;
import com.university.lms.communication.dto.ConversationComplianceExport;
import com.university.lms.communication.dto.ConversationSummaryResponse;
import com.university.lms.communication.dto.CreateAnnouncementRequest;
import com.university.lms.communication.dto.CreateConversationRequest;
import com.university.lms.communication.dto.CreateMessageRequest;
import com.university.lms.communication.dto.MessageResponse;
import com.university.lms.communication.dto.StartConversationResult;
import com.university.lms.communication.service.CommunicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CommunicationController {

    private final CommunicationService communicationService;

    public CommunicationController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @PostMapping("/announcements")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(
            @Valid @RequestBody CreateAnnouncementRequest request) {
        AnnouncementResponse created = communicationService.createAnnouncement(request);
        return ResponseEntity.created(URI.create("/api/v1/announcements/" + created.id())).body(created);
    }

    @PostMapping("/announcements/{id}/publish")
    public AnnouncementResponse publishAnnouncement(@PathVariable UUID id) {
        return communicationService.publishAnnouncement(id);
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationSummaryResponse> start(@Valid @RequestBody CreateConversationRequest request) {
        StartConversationResult result = communicationService.startConversation(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/conversations/" + result.conversation().id()))
                    .body(result.conversation());
        }
        return ResponseEntity.ok(result.conversation());
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> send(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMessageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CommunicationService.SendMessageResult result = communicationService.send(id, request, idempotencyKey);
        MessageResponse body = result.message();
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/conversations/" + id + "/messages/" + body.id()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/conversations/{id}/compliance-export")
    public ConversationComplianceExport complianceExport(@PathVariable UUID id) {
        return communicationService.exportConversationCompliance(id);
    }
}
