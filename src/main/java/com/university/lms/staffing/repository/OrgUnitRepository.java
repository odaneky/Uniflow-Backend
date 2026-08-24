package com.university.lms.staffing.repository;

import com.university.lms.staffing.domain.OrgUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the staffing module. */
public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    Optional<OrgUnit> findByCode(String code);

    List<OrgUnit> findByParentId(UUID parentId);

    boolean existsByCode(String code);

    boolean existsBySourceTypeAndSourceId(String sourceType, UUID sourceId);
}
