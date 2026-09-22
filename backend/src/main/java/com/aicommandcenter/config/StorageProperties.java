package com.aicommandcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String location,
        long maxFileSizeBytes,
        List<String> allowedExtensions,
        List<String> allowedContentTypes) {
}
