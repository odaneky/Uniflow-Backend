package com.university.lms.identity.dto;

import com.university.lms.identity.api.CurrentUser;
import java.util.List;
import java.util.UUID;

/**
 * The caller's own identity.
 *
 * <p>Carries {@code userId} because it is the id every other endpoint is addressed by — without it
 * an authenticated caller holds nothing they could use to ask about their own records.
 *
 * <p>{@code accountManagementUrl} is where a client sends someone who wants to change their
 * password. UniFlow renders a link, never a form: it has no password to change.
 */
public record CurrentUserResponse(
        UUID userId,
        String externalIdentityId,
        String username,
        String studentNumber,
        String email,
        String fullName,
        List<String> roles,
        List<String> permissions,
        String accountManagementUrl) {

    public static CurrentUserResponse from(CurrentUser user, String accountManagementUrl) {
        return new CurrentUserResponse(
                user.userId(),
                user.externalIdentityId(),
                user.username(),
                user.studentNumber().orElse(null),
                user.email(),
                user.fullName(),
                user.roles().stream().sorted().toList(),
                user.permissions().stream().sorted().toList(),
                accountManagementUrl);
    }
}
