package com.university.lms.document.scan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link VirusScanner} for any deployment with no scanner configured. Reports every file
 * {@code NOT_SCANNED} — never a faked {@code CLEAN} — so a caller that gates on the scan result
 * (see {@code DocumentService.store}) makes an honest decision about an environment that simply
 * isn't scanning, rather than being told a file is safe when nothing checked it.
 */
@Component
@ConditionalOnProperty(name = "lms.virus-scan.enabled", havingValue = "false", matchIfMissing = true)
public class NoopVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(byte[] content) {
        return ScanResult.NOT_SCANNED;
    }
}
