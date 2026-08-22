package com.university.lms.finance.repository;

import com.university.lms.finance.domain.AccountEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, UUID> {

    List<AccountEntry> findByAccountIdOrderByOccurredAtAsc(UUID accountId);

    boolean existsByAccountIdAndReference(UUID accountId, String reference);

    java.util.Optional<AccountEntry> findByAccountIdAndReference(UUID accountId, String reference);
}
