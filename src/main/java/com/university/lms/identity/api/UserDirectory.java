package com.university.lms.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The identity module's published contract.
 *
 * <p>Modules that merely need to confirm a person exists — the course module validating a
 * lecturer, the student module linking an account — depend on this interface, never on
 * {@code identity.repository}. That keeps identity free to change its persistence internals, and
 * means extracting it into its own service later is a change of implementation rather than a
 * rewrite of every caller.
 */
public interface UserDirectory {

    /** A read-only projection; deliberately carries no credential or status-transition capability. */
    record UserSummary(UUID id, String username, String fullName, String email, boolean active) {}

    boolean exists(UUID userId);

    Optional<UserSummary> findById(UUID userId);

    /**
     * Local users who hold this realm role at the identity provider.
     *
     * <p>Empty when the provider is unavailable, the role has no members, or a Keycloak account
     * has not been projected into UniFlow yet.
     */
    java.util.List<UserSummary> findByRealmRole(String role);

    /** Active local accounts — used for university-wide announcement fan-out. */
    java.util.List<UUID> activeUserIds();
}
