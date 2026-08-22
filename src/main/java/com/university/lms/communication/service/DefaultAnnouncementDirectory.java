package com.university.lms.communication.service;

import com.university.lms.communication.api.AnnouncementDirectory;
import com.university.lms.communication.domain.Announcement;
import com.university.lms.communication.domain.AnnouncementAudience;
import com.university.lms.communication.repository.AnnouncementRepository;
import com.university.lms.course.api.CourseCatalog;
import com.university.lms.enrollment.api.EnrollmentDirectory;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultAnnouncementDirectory implements AnnouncementDirectory {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnnouncementDirectory.class);

    private final AnnouncementRepository announcementRepository;
    private final UserDirectory userDirectory;
    private final StudentDirectory studentDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final CourseCatalog courseCatalog;

    public DefaultAnnouncementDirectory(
            AnnouncementRepository announcementRepository,
            UserDirectory userDirectory,
            StudentDirectory studentDirectory,
            EnrollmentDirectory enrollmentDirectory,
            CourseCatalog courseCatalog) {
        this.announcementRepository = announcementRepository;
        this.userDirectory = userDirectory;
        this.studentDirectory = studentDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.courseCatalog = courseCatalog;
    }

    @Override
    public Optional<AnnouncementSummary> findPublishedById(UUID announcementId) {
        return announcementRepository
                .findById(announcementId)
                .filter(Announcement::isPublished)
                .map(this::toSummary);
    }

    @Override
    public List<UUID> recipientUserIds(AnnouncementSummary announcement) {
        Set<UUID> recipients = new LinkedHashSet<>();
        switch (announcement.audience()) {
            case UNIVERSITY_WIDE -> recipients.addAll(userDirectory.activeUserIds());
            case PROGRAMME -> recipients.addAll(studentDirectory.userIdsByProgramme(announcement.audienceRefId()));
            case COURSE_SECTION -> {
                UUID sectionId = announcement.audienceRefId();
                if (sectionId != null) {
                    for (var row : enrollmentDirectory.rosterOf(sectionId)) {
                        studentDirectory
                                .findById(row.studentId())
                                .ifPresent(s -> recipients.add(s.userId()));
                    }
                    courseCatalog
                            .findSection(sectionId)
                            .map(CourseCatalog.SectionSummary::lecturerUserId)
                            .ifPresent(recipients::add);
                }
            }
            case FACULTY, DEPARTMENT -> log.warn(
                    "Announcement audience {} fan-out not implemented for id {}",
                    announcement.audience(),
                    announcement.id());
            default -> { }
        }
        recipients.remove(announcement.authorUserId());
        return List.copyOf(recipients);
    }

    private AnnouncementSummary toSummary(Announcement announcement) {
        String body = announcement.getBody();
        if (body.length() > 160) {
            body = body.substring(0, 157) + "...";
        }
        return new AnnouncementSummary(
                announcement.getId(),
                announcement.getTitle(),
                body,
                announcement.getAudience(),
                announcement.getAudienceRefId(),
                announcement.getAuthorUserId());
    }
}
