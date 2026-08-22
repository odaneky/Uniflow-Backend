package com.university.lms.identity.service;

import com.university.lms.identity.api.UserDirectory;
import com.university.lms.identity.repository.UserRepository;
import com.university.lms.identity.spi.IdentityProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapts the identity module's internals to its published {@link UserDirectory} contract. */
@Service
@Transactional(readOnly = true)
public class DefaultUserDirectory implements UserDirectory {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserDirectory.class);

    private final UserRepository userRepository;
    private final IdentityProvider identityProvider;

    public DefaultUserDirectory(UserRepository userRepository, IdentityProvider identityProvider) {
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
    }

    @Override
    public boolean exists(UUID userId) {
        return userId != null && userRepository.existsById(userId);
    }

    @Override
    public Optional<UserSummary> findById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository
                .findById(userId)
                .map(user -> new UserSummary(
                        user.getId(), user.getUsername(), user.fullName(), user.getEmail(), user.canAuthenticate()));
    }

    @Override
    public List<UserSummary> findByRealmRole(String role) {
        if (role == null || role.isBlank() || !identityProvider.isAvailable()) {
            return List.of();
        }
        try {
            List<UserSummary> found = new ArrayList<>();
            for (String subject : identityProvider.subjectsWithRealmRole(role)) {
                userRepository.findByKeycloakSubject(subject).ifPresent(user -> found.add(new UserSummary(
                        user.getId(),
                        user.getUsername(),
                        user.fullName(),
                        user.getEmail(),
                        user.canAuthenticate())));
            }
            return List.copyOf(found);
        } catch (RuntimeException ex) {
            log.warn("Could not list users with role {}", role, ex);
            return List.of();
        }
    }
}
