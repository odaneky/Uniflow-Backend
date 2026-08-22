package com.university.lms.identity.repository;

import com.university.lms.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the identity module — other modules go through {@code identity.api.UserDirectory}. */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** The join from a bearer token to a domain identity; backed by {@code uk_users_keycloak_subject}. */
    Optional<User> findByKeycloakSubject(String keycloakSubject);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);
}
