package com.university.lms.identity.domain;

import com.university.lms.common.exception.ErrorCode;

/**
 * Error vocabulary owned by the identity module.
 *
 * <p>Identity-provider failures are translated into these before they reach a client. A Keycloak
 * status code, error body or stack trace must never be passed through: it leaks the topology of the
 * identity estate and gives an unauthenticated caller a view of infrastructure they have no business
 * seeing.
 */
public enum IdentityErrorCode implements ErrorCode {

    USER_NOT_FOUND,
    USERNAME_ALREADY_EXISTS,
    EMAIL_ALREADY_EXISTS,
    ROLE_NOT_FOUND,

    /** A verified identity-provider attribute already belongs to a different subject. */
    IDENTITY_CONFLICT,
    /** Refusing to adopt an existing local account on the strength of an unverified email. */
    EMAIL_NOT_VERIFIED,
    /** The token could not be resolved to a local account. */
    IDENTITY_NOT_LINKED,
    /** The identity provider is unreachable, misconfigured, or rejected an administrative call. */
    IDENTITY_SYNC_FAILED,
    /** Identity-provider administration is not configured in this environment. */
    IDENTITY_PROVIDER_UNAVAILABLE,
    /** The account exists but the identity provider will not authenticate it. */
    ACCOUNT_INACTIVE,

    ROLE_ALREADY_GRANTED,
    INVALID_USER_STATE;

    @Override
    public String code() {
        return name();
    }
}
