package com.university.lms.identity.dto;

import com.university.lms.identity.domain.User;
import com.university.lms.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A user account as returned by the API.
 *
 * <p>There is deliberately no field for the password hash. Omitting it here — rather than relying
 * on a serialisation annotation on the entity — means no future change to the entity can leak it.
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String fullName,
        UserStatus status,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.fullName(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
