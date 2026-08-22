package com.university.lms.communication.api;

import com.university.lms.identity.api.CurrentUser;
import java.util.Set;
import java.util.UUID;

/** Campus-real messaging authorization — enforced in the service layer, never in the UI. */
public interface MessagingPolicy {

    void assertCanStartConversation(
            CurrentUser caller, Set<UUID> participantUserIds, UUID courseSectionId);

    void assertCanSendMessage(CurrentUser caller, UUID conversationId);

    void assertCanReadConversation(CurrentUser caller, UUID conversationId);

    void assertCanAddParticipant(CurrentUser caller, UUID conversationId, UUID newParticipantUserId);
}
