package com.university.lms.student.api;

import com.university.lms.student.dto.UpdateOwnProfileRequest;
import java.util.UUID;

/** Student standing changes triggered by approved registry requests. */
public interface StudentLifecycle {

    void graduate(UUID studentId, UUID actorUserId);

    void applyContactCorrection(UUID studentId, UpdateOwnProfileRequest contact);

    void beginLeave(UUID studentId, UUID actorUserId);

    void readmit(UUID studentId, UUID actorUserId);
}
