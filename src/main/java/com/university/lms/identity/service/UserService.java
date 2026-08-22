package com.university.lms.identity.service;

import com.university.lms.common.dto.PageResponse;
import com.university.lms.common.exception.ResourceNotFoundException;
import com.university.lms.identity.domain.IdentityErrorCode;
import com.university.lms.identity.domain.User;
import com.university.lms.identity.dto.UserResponse;
import com.university.lms.identity.dto.UserSummaryResponse;
import com.university.lms.identity.repository.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads over the local projection of identities.
 *
 * <p>Deliberately read-only. Everything that changes an account — provisioning it, disabling it,
 * granting a role — has an effect at the identity provider and lives in
 * {@link IdentityProvisioningService}. Keeping the two apart means no operation can accidentally
 * update only the local copy and appear to have taken effect.
 *
 * <p>These rows are a projection, not a source of truth. The identity provider is authoritative for
 * whether an account can authenticate and what roles it holds; this is the local record of who a
 * person is within the university's academic systems.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse findById(UUID userId) {
        return UserResponse.from(require(userId));
    }

    public PageResponse<UserSummaryResponse> findAll(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable), UserSummaryResponse::from);
    }

    private User require(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        IdentityErrorCode.USER_NOT_FOUND, "No user exists with id " + userId));
    }
}
