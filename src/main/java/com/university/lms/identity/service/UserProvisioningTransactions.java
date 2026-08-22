package com.university.lms.identity.service;

import com.university.lms.identity.domain.User;
import com.university.lms.identity.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundaries for provisioning, kept in their own bean.
 *
 * <p>Two things force this shape.
 *
 * <p><b>Writes must not join the caller's transaction.</b> Ownership checks run inside
 * {@code readOnly = true} service methods, and an insert attempted there fails.
 *
 * <p><b>Race recovery must run in a fresh transaction.</b> When two first requests from the same
 * person collide, the loser's insert violates the unique index — and PostgreSQL then refuses every
 * further statement in that transaction with "current transaction is aborted". Reading back the
 * winner's row therefore cannot happen in the transaction that just failed; it needs a new one.
 * That was a real defect, and it only appeared under an actual concurrent test.
 *
 * <p>{@code REQUIRES_NEW} takes effect through the proxy, so these cannot be private methods on the
 * calling service — a sibling call would silently run in the caller's transaction instead.
 */
@Service
class UserProvisioningTransactions {

    private final UserRepository userRepository;

    UserProvisioningTransactions(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    User save(User user) {
        return userRepository.saveAndFlush(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<User> findBySubject(String subject) {
        return userRepository.findByKeycloakSubject(subject);
    }
}
