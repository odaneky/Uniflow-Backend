package com.university.lms.course.repository;

import com.university.lms.course.domain.SectionComponent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SectionComponentRepository extends JpaRepository<SectionComponent, UUID> {

    List<SectionComponent> findBySectionId(UUID sectionId);

    void deleteBySectionId(UUID sectionId);

    boolean existsBySectionIdAndLecturerUserId(UUID sectionId, UUID lecturerUserId);

    List<SectionComponent> findByLecturerUserIdIsNotNull();

    @Query(
            """
            select sc from SectionComponent sc
            join fetch sc.section s
            join fetch s.course
            where sc.lecturerUserId is not null
            """)
    List<SectionComponent> findAssignedWithSection();

    @Query(
            """
            select sc from SectionComponent sc
            join fetch sc.section s
            join fetch s.course
            where sc.lecturerUserId = :lecturerUserId
            """)
    List<SectionComponent> findByLecturerUserIdWithSection(@Param("lecturerUserId") UUID lecturerUserId);
}
