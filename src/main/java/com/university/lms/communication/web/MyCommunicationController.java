package com.university.lms.communication.web;

import com.university.lms.common.dto.CursorPageResponse;
import com.university.lms.common.dto.PageResponse;
import com.university.lms.communication.dto.AnnouncementResponse;
import com.university.lms.communication.dto.ConversationSummaryResponse;
import com.university.lms.communication.dto.MessageResponse;
import com.university.lms.communication.service.CommunicationService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MyCommunicationController {

    private final CommunicationService communicationService;

    public MyCommunicationController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping("/announcements")
    public List<AnnouncementResponse> announcements() {
        return communicationService.ownAnnouncements();
    }

    @PostMapping("/announcements/{id}/read")
    public void markAnnouncementRead(@PathVariable UUID id) {
        communicationService.markAnnouncementRead(id);
    }

    @GetMapping("/conversations")
    public PageResponse<ConversationSummaryResponse> conversations(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return communicationService.ownConversations(pageable);
    }

    @GetMapping("/conversations/{id}/messages")
    public CursorPageResponse<MessageResponse> messages(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size) {
        return communicationService.ownMessages(id, cursor, size);
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    public void deleteMessage(@PathVariable UUID conversationId, @PathVariable UUID messageId) {
        communicationService.deleteOwnMessage(conversationId, messageId);
    }

    @PostMapping("/conversations/{id}/read")
    public void markConversationRead(@PathVariable UUID id) {
        communicationService.markConversationRead(id);
    }

    @GetMapping("/conversations/unread-count")
    public UnreadCountResponse conversationUnreadCount() {
        return new UnreadCountResponse(communicationService.ownUnreadConversationCount());
    }

    public record UnreadCountResponse(long count) {}
}
