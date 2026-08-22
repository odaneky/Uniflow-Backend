package com.university.lms.grading.api;

import java.util.UUID;

/** Grade appeal lifecycle driven by approved service requests. */
public interface GradeAppealActions {

    void openAppeal(UUID gradeId, UUID actorUserId);

    void resolveAppeal(UUID gradeId, UUID actorUserId);
}
