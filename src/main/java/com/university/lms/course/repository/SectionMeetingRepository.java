package com.university.lms.course.repository;

import com.university.lms.course.domain.SectionMeeting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface SectionMeetingRepository extends JpaRepository<SectionMeeting, UUID> {

    List<SectionMeeting> findBySectionIdOrderByPositionAsc(UUID sectionId);

    void deleteBySectionId(UUID sectionId);

    /**
     * Every meeting booked into a room for a term, for room double-booking checks.
     *
     * <p>Narrowed by room in the query rather than loading the term and filtering in memory: this
     * runs on an ordinary admin action, and scanning every meeting in the university to validate one
     * edit is how a save button becomes a performance incident.
     *
     * <p>{@code room} must already be lower-cased by the caller. Lowering a bare parameter inside a
     * SQL function is the shape that once left Hibernate unable to infer a type and emitted
     * {@code lower(bytea)} at runtime.
     */
    @Query("""
            select m from SectionMeeting m
            join fetch m.section s
            join fetch s.course
            where s.academicTermId = :academicTermId
              and lower(m.room) = :room
            """)
    List<SectionMeeting> findByTermAndRoom(
            @Param("academicTermId") UUID academicTermId, @Param("room") String room);
}
