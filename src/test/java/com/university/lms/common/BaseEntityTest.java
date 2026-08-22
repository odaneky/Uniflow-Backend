package com.university.lms.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.identity.domain.Permission;
import com.university.lms.identity.domain.Role;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Entity equality is a classic JPA trap: identifiers assigned by the database are null until
 * flush, so an entity put into a {@link Set} before saving becomes unfindable afterwards. The id
 * is assigned in the constructor precisely to avoid that, and these tests hold that guarantee.
 */
class BaseEntityTest {

    @Test
    @DisplayName("an unsaved entity already has a stable identifier")
    void identifierIsAssignedAtConstruction() {
        Role role = new Role("REGISTRAR", "Owns student records");
        assertThat(role.getId()).isNotNull();
        assertThat(role.isNew()).isTrue();
    }

    @Test
    @DisplayName("hash code survives being added to a collection before persistence")
    void remainsFindableInAHashSetAcrossMutation() {
        Role role = new Role("REGISTRAR", "Owns student records");
        Set<Role> roles = new HashSet<>();
        roles.add(role);

        role.grant(new Permission("STUDENT_READ", "View student records"));

        assertThat(roles).contains(role);
    }

    @Test
    @DisplayName("two distinct instances are never equal")
    void distinctInstancesAreNotEqual() {
        assertThat(new Role("A", null)).isNotEqualTo(new Role("A", null));
    }

    @Test
    @DisplayName("entities of different types are not equal")
    void differentTypesAreNotEqual() {
        assertThat((Object) new Role("A", null)).isNotEqualTo(new Permission("A", null));
    }

    @Test
    void equalsIsReflexive() {
        Role role = new Role("REGISTRAR", null);
        assertThat(role).isEqualTo(role);
        assertThat(role.hashCode()).isEqualTo(role.hashCode());
    }
}
