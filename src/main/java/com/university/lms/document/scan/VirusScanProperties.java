package com.university.lms.document.scan;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code enabled=false} (default) registers {@code NoopVirusScanner}; {@code enabled=true}
 * registers {@code ClamAvVirusScanner} against a ClamAV daemon at {@code host}:{@code port}.
 */
@ConfigurationProperties("lms.virus-scan")
public record VirusScanProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("localhost") String host,
        @DefaultValue("3310") int port) {}
