package com.university.lms.document.scan;

/**
 * F4: whether uploaded bytes are checked for malware before they are stored. Exactly one
 * implementation is active at a time, selected by {@code lms.virus-scan.enabled} — see {@code
 * NoopVirusScanner} (the honest default: no scanner configured means every file reports {@code
 * NOT_SCANNED}, never a faked {@code CLEAN}) and {@code ClamAvVirusScanner} (a real scan against a
 * ClamAV daemon, thrown as {@code DocumentStoreException} rather than silently allowed through if
 * the daemon is unreachable).
 */
public interface VirusScanner {

    ScanResult scan(byte[] content);

    enum ScanResult {
        CLEAN,
        INFECTED,
        NOT_SCANNED
    }
}
