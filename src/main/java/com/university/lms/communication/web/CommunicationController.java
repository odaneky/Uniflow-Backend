package com.university.lms.communication.web;

import com.university.lms.communication.dto.AnnouncementResponse;
import com.university.lms.communication.dto.ConversationSummaryResponse;
import com.university.lms.communication.dto.CreateAnnouncementRequest;
import com.university.lms.communication.dto.CreateConversationRequest;
import com.university.lms.communication.dto.CreateMessageRequest;
import com.university.lms.communication.dto.MessageResponse;
import com.university.lms.communication.service.CommunicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        ConversationSummaryResponse created = communicationService.startConversation(request);
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + created.id())).body(created);
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> send(
            @PathVariable UUID id, @Valid @RequestBody CreateMessageRequest request) {
        MessageResponse created = communicationService.send(id, request);
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + id + "/messages/" + created.id()))
                .body(created);
    }
}
