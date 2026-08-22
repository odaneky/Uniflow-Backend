package com.university.lms.communication.repository;

import com.university.lms.communication.domain.AnnouncementRead;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementRead.AnnouncementReadId> {

    boolean existsByAnnouncementIdAndUserId(UUID announcementId, UUID userId);

    @Query(
            """
            select r.announcementId from AnnouncementRead r
            where r.userId = :userId and r.announcementId in :announcementIds
            """)
    Set<UUID> findReadAnnouncementIdsForUser(
            @Param("userId") UUID userId, @Param("announcementIds") Collection<UUID> announcementIds);
}
