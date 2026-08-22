package com.university.lms.communication.web;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.communication.dto.CreateForumPostRequest;
import com.university.lms.communication.dto.CreateForumTopicRequest;
import com.university.lms.communication.dto.ForumPostResponse;
import com.university.lms.communication.dto.ForumTopicResponse;
import com.university.lms.communication.dto.UpdateForumTopicRequest;
import com.university.lms.communication.service.ForumService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ForumController {

    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/forum/topics/{topicId}")
    public ForumTopicResponse topic(@PathVariable UUID topicId) {
        return forumService.getTopic(topicId);
    }

    @GetMapping("/forum/topics/{topicId}/posts")
    public PageResponse<ForumPostResponse> posts(
            @PathVariable UUID topicId,
            @PageableDefault(size = 50, sort = "sentAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return forumService.listPosts(topicId, pageable);
    }

    @PostMapping("/forum/topics/{topicId}/posts")
    public ResponseEntity<ForumPostResponse> createPost(
            @PathVariable UUID topicId, @Valid @RequestBody CreateForumPostRequest request) {
        ForumPostResponse created = forumService.createPost(topicId, request);
        return ResponseEntity.created(URI.create("/api/v1/forum/posts/" + created.id())).body(created);
    }

    @PatchMapping("/forum/topics/{topicId}")
    public ForumTopicResponse updateTopic(
            @PathVariable UUID topicId, @Valid @RequestBody UpdateForumTopicRequest request) {
        return forumService.updateTopic(topicId, request);
    }

    @DeleteMapping("/forum/posts/{postId}")
    public void deletePost(@PathVariable UUID postId) {
        forumService.deletePost(postId);
    }
}
