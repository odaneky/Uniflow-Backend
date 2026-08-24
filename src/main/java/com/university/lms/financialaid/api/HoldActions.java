package com.university.lms.financialaid.api;

import java.util.UUID;

/** Hold mutations driven by approved service requests. */
public interface HoldActions {

    /** Clears every active SAP hold on this student, following an approved SAP appeal. */
    void clearSapHold(UUID studentId);
}
