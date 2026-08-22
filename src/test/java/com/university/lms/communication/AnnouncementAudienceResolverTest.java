package com.university.lms.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.university.lms.communication.api.AnnouncementDirectory;
import com.university.lms.communication.domain.AnnouncementAudience;
import com.university.lms.identity.api.UserDirectory;
import com.university.lms.student.api.StudentDirectory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnnouncementAudienceResolverTest {

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private StudentDirectory studentDirectory;

    private DefaultAnnouncementDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new DefaultAnnouncementDirectory(
                null, userDirectory, studentDirectory, null, null);
    }

    @Test
    void universityWideIncludesActiveUsersExceptAuthor() {
        UUID author = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(userDirectory.activeUserIds()).thenReturn(List.of(author, u1, u2));

        AnnouncementDirectory.AnnouncementSummary summary = new AnnouncementDirectory.AnnouncementSummary(
                UUID.randomUUID(), "Title", "Body", AnnouncementAudience.UNIVERSITY_WIDE, null, author);

        assertThat(directory.recipientUserIds(summary)).containsExactlyInAnyOrder(u1, u2);
    }
}
