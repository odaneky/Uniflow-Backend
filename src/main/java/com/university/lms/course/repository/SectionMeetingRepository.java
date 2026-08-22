package com.university.lms.course.repository;

import com.university.lms.course.domain.SectionMeeting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionMeetingRepository extends JpaRepository<SectionMeeting, UUID> {

    List<SectionMeeting> findBySectionIdOrderByPositionAsc(UUID sectionId);

    void deleteBySectionId(UUID sectionId);
}
