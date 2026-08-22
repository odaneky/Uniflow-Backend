package com.university.lms.identity.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * A login account.
 *
 * <p>This entity owns identity only — who someone is and whether they may sign in. It deliberately
 * does <em>not</em> accumulate academic attributes: a student's programme and matriculation number
 * live on {@code student.domain.Student}, a lecturer's teaching load lives in the course module.
 * Letting those grow here is how a {@code User} table ends up with ninety nullable columns and
 * every module coupled to it.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
            @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        },
        indexes = @Index(name = "idx_users_status", columnList = "status"))
@Getter
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * The identity provider's {@code sub} claim — the one thing that ties a bearer token to this
     * row. Null only for rows created before the link existed, which can therefore never be logged
     * in as.
     */
    @Column(name = "keycloak_subject", length = 255)
    private String keycloakSubject;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING_ACTIVATION;

    protected User() {
        // for JPA
    }

    /**
     * A local projection of an identity the provider already holds.
     *
     * <p>There is no credential parameter, and there must never be one: this row records who a
     * person is within the university's academic systems, not how they prove it.
     */
    public User(String username, String email, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * A user provisioned from a bearer token on first sight.
     *
     * <p>Created {@code ACTIVE} rather than {@code PENDING_ACTIVATION}: the identity provider has
     * already authenticated this person, and a local activation step they cannot reach would lock
     * out everyone who has never been enrolled by hand. What they may *do* is decided by the roles
     * in their token, not by this row.
     */
    public static User fromIdentityProvider(
            String subject, String username, String email, String firstName, String lastName) {
        User user = new User(username, email, firstName, lastName);
        user.keycloakSubject = subject;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    /** Links an existing local row to an identity-provider subject. */
    public void linkToIdentityProvider(String subject) {
        this.keycloakSubject = subject;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean canAuthenticate() {
        return status == UserStatus.ACTIVE;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
    }


    public void rename(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void changeEmail(String email) {
        this.email = email;
    }
}
