package com.university.lms.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lms.storage")
public record StorageProperties(String localRoot, long maxUploadBytes) {}
