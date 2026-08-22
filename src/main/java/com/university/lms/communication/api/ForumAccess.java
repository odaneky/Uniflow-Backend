package com.university.lms.communication.api;

import com.university.lms.identity.api.CurrentUser;
import java.util.UUID;

public interface ForumAccess {

    void assertCanReadForum(CurrentUser caller, UUID courseSectionId);

    void assertCanPostForum(CurrentUser caller, UUID courseSectionId);

    void assertCanModerateForum(CurrentUser caller, UUID courseSectionId);
}
