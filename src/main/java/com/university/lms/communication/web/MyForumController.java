package com.university.lms.communication.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.communication.dto.CreateForumTopicRequest;
import com.university.lms.communication.dto.ForumTopicResponse;
import com.university.lms.communication.service.ForumService;
import com.university.lms.common.security.AccessClass;
import static com.university.lms.common.security.AccessClass.Value.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/courses")
public class MyForumController {

    private final ForumService forumService;

    public MyForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/{sectionId}/forum/topics")
    @AccessClass(SELF_OR_STAFF)
    public PageResponse<ForumTopicResponse> topics(
            @PathVariable UUID sectionId,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return forumService.listTopics(sectionId, pageable);
    }

    @PostMapping("/{sectionId}/forum/topics")
    @AccessClass(SELF_OR_STAFF)
    public ResponseEntity<ForumTopicResponse> createTopic(
            @PathVariable UUID sectionId, @Valid @RequestBody CreateForumTopicRequest request) {
        ForumTopicResponse created = forumService.createTopic(sectionId, request);
        return ResponseEntity.created(URI.create("/api/v1/forum/topics/" + created.id())).body(created);
    }
}
