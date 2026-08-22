package com.university.lms.identity.repository;

import com.university.lms.identity.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    /**
     * Fetch-joins permissions so resolving a caller's permission set is one query per role rather
     * than one query per role plus one lazy load each — this runs on every authenticated request
     * that asks a permission question.
     */
    @org.springframework.data.jpa.repository.Query(
            "select r from Role r left join fetch r.permissions where r.name = :name")
    Optional<Role> findByNameWithPermissions(@org.springframework.data.repository.query.Param("name") String name);

    boolean existsByName(String name);
}
