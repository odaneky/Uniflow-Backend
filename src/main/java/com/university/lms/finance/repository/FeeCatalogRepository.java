package com.university.lms.finance.repository;

import com.university.lms.finance.domain.FeeCatalogItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeCatalogRepository extends JpaRepository<FeeCatalogItem, UUID> {

    List<FeeCatalogItem> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
