package com.university.lms.finance.repository;

import com.university.lms.finance.domain.AccountEntry;
import com.university.lms.finance.domain.AccountEntryStatus;
import com.university.lms.finance.domain.AccountEntryType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEntryRepository extends JpaRepository<AccountEntry, UUID> {

    List<AccountEntry> findByAccountIdOrderByOccurredAtAsc(UUID accountId);

    boolean existsByAccountIdAndReference(UUID accountId, String reference);

    java.util.Optional<AccountEntry> findByAccountIdAndReference(UUID accountId, String reference);

    /** E6: what an invoice for this student's term bundles — posted charges only. */
    List<AccountEntry> findByAccountIdAndAcademicTermIdAndEntryTypeAndStatusOrderByOccurredAtAsc(
            UUID accountId, UUID academicTermId, AccountEntryType entryType, AccountEntryStatus status);
}
