package com.university.lms.identity.repository;

import com.university.lms.identity.domain.User;
import com.university.lms.identity.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Internal to the identity module — other modules go through {@code identity.api.UserDirectory}. */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** The join from a bearer token to a domain identity; backed by {@code uk_users_keycloak_subject}. */
    Optional<User> findByKeycloakSubject(String keycloakSubject);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u.id from User u where u.status = :status")
    java.util.List<UUID> findIdsByStatus(UserStatus status);
}
