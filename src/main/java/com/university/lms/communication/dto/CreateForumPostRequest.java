package com.university.lms.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateForumPostRequest(
        @NotBlank @Size(max = 4000) String body, UUID parentPostId) {}
