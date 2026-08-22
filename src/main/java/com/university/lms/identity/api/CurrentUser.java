package com.university.lms.identity.api;

import com.university.lms.common.exception.CommonErrorCode;
import com.university.lms.common.exception.ForbiddenException;
import com.university.lms.common.security.SecurityRoles;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The caller, resolved from a bearer token to a domain identity.
 *
 * <p>A token on its own cannot answer ownership questions: it carries an external identity and a
 * list of roles, and nothing that names a row in this database. This record is the result of the
 * join, and it is what makes "is this record yours" a question the service layer can ask at all.
 *
 * <p>The application layer depends on this type, never on {@code Jwt}, {@code SecurityContextHolder}
 * or any other Spring Security detail — so replacing the identity provider, or the security
 * framework, does not reach into business code.
 *
 * @param userId the local user row; the id every other endpoint is addressed by
 * @param externalIdentityId the identity provider's immutable subject. Never a username: usernames
 *     can be reassigned, and an identifier that can be reassigned must not key authorization.
 * @param studentNumber the institutional identifier, present only when the caller is a student and
 *     the identity provider asserts it
 * @param roles as asserted by the token. The provider is authoritative for these; a local table
 *     saying otherwise would be a second answer free to drift from the first.
 */
public record CurrentUser(
        UUID userId,
        String externalIdentityId,
        String username,
        String email,
        String fullName,
        Optional<String> studentNumber,
        Set<String> roles,
        Set<String> permissions) {

    /**
     * Every role except {@code STUDENT}. Defined by exclusion deliberately: a role added later is
     * staff until someone decides otherwise, so the failure mode of forgetting to update this is a
     * refusal to a member of staff rather than silent exposure of student data.
     */
    public boolean isStaff() {
        return roles.stream().anyMatch(role -> !SecurityRoles.STUDENT.equals(role));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * UniFlow's own permission model, resolved from the caller's roles.
     *
     * <p>Distinct from {@link #hasRole}: a role is a coarse label the identity provider asserts,
     * while a permission is what this application decides that label may do. Neither is sufficient
     * for a resource-level decision — holding {@code COURSE_EDIT} does not make a lecturer the
     * teacher of a particular section — so ownership is still checked separately.
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Guards a record owned by a user.
     *
     * <p>Refuses with the same code and message whether the record belongs to someone else or does
     * not exist as far as this caller is concerned. A distinct "no such record" would let a student
     * enumerate which ids are real, which is a slow but complete disclosure of the roster.
     */
    public void requireSelfOrStaff(UUID ownerUserId) {
        if (isStaff() || userId.equals(ownerUserId)) {
            return;
        }
        throw new ForbiddenException(
                CommonErrorCode.ACCESS_DENIED, "You do not have permission to access this record");
    }
}
