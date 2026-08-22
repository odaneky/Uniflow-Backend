package com.university.lms.identity.dto;

import com.university.lms.identity.domain.User;
import com.university.lms.identity.domain.UserStatus;
import java.util.UUID;

/** Compact representation for list endpoints. */
public record UserSummaryResponse(UUID id, String username, String fullName, String email, UserStatus status) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(), user.getUsername(), user.fullName(), user.getEmail(), user.getStatus());
    }
}
