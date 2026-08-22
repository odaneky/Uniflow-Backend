package com.university.lms.communication.repository;

import com.university.lms.communication.domain.Announcement;
import com.university.lms.communication.domain.AnnouncementAudience;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal to the communication module. */
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    Page<Announcement> findByAudienceAndAudienceRefIdAndPublishedAtIsNotNull(
            AnnouncementAudience audience, UUID audienceRefId, Pageable pageable);

    List<Announcement> findByAudienceAndPublishedAtIsNotNullOrderByPublishedAtDesc(AnnouncementAudience audience);

    @Query(
            """
            select a from Announcement a
            where a.publishedAt is not null
              and (
                    a.audience = :university
                 or (a.audience = :programmeAudience and a.audienceRefId = :programmeId)
                 or (a.audience = :sectionAudience and a.audienceRefId in :sectionIds)
              )
            order by a.publishedAt desc
            """)
    List<Announcement> findVisibleTo(
            @Param("university") AnnouncementAudience university,
            @Param("programmeAudience") AnnouncementAudience programmeAudience,
            @Param("sectionAudience") AnnouncementAudience sectionAudience,
            @Param("programmeId") UUID programmeId,
            @Param("sectionIds") Collection<UUID> sectionIds);
}
