package com.university.lms.document.domain;

/**
 * F4: whether an uploaded file was actually checked for malware. {@code NOT_SCANNED} is the
 * honest default in any deployment with no scanner configured — see {@code NoopVirusScanner} —
 * distinct from {@code CLEAN}, which only a real scanner may claim.
 */
public enum VirusScanStatus {
    NOT_SCANNED,
    CLEAN,
    INFECTED
}
