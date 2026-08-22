package com.university.lms.financialaid.api;

import java.util.List;
import java.util.UUID;

/**
 * Published contract for registration-blocking holds.
 *
 * <p>Enrolment calls this interface rather than reading {@code service_holds} or {@code sap_evaluations}
 * directly. Financial payment-plan holds remain owned by {@code finance.api.StudentBilling}; enrolment
 * combines both via {@code requireNoRegistrationHolds()}.
 */
public interface RegistrationHolds {

    record HoldDetail(String type, String reason) {}

    /** Active service and SAP holds that block registration for this student. */
    List<HoldDetail> activeRegistrationHolds(UUID studentId);

    default boolean blocksRegistration(UUID studentId) {
        return !activeRegistrationHolds(studentId).isEmpty();
    }
}
