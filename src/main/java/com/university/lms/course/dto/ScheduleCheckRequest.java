package com.university.lms.course.dto;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * "Would this clash?" — asked before anything is saved.
 *
 * <p>Exists so the editor can warn while someone is still choosing, rather than letting them fill in
 * a form and discover on save that the lecturer is teaching elsewhere. It runs the same checker the
 * write path runs, so the warning and the refusal cannot drift apart — a preview computed
 * independently would eventually disagree with the rule it is previewing, and the disagreement would
 * surface as a save that fails for no visible reason.
 *
 * @param lecturerUserId the lecturer being considered, or null to check only rooms
 * @param meetings the proposed sessions, or null to check the section's current ones
 */
public record ScheduleCheckRequest(
        UUID lecturerUserId, @Valid List<ReplaceSectionMeetingsRequest.MeetingRequest> meetings) {}
