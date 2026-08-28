package com.university.lms.document.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.university.lms.document.scan.VirusScanner.ScanResult;
import org.junit.jupiter.api.Test;

/**
 * The default scanner for any deployment with {@code lms.virus-scan.enabled=false} (or unset).
 * Pins the honest-default contract: it must never report {@code CLEAN} — that would tell a caller
 * a file is safe when nothing actually checked it.
 */
class NoopVirusScannerTest {

    @Test
    void alwaysReportsNotScannedRegardlessOfContent() {
        NoopVirusScanner scanner = new NoopVirusScanner();

        assertThat(scanner.scan("harmless".getBytes())).isEqualTo(ScanResult.NOT_SCANNED);
        assertThat(scanner.scan(new byte[0])).isEqualTo(ScanResult.NOT_SCANNED);
    }
}
