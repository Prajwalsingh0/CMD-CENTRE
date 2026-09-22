package com.aicommandcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.research")
public record ResearchProperties(boolean webEnabled, int httpTimeoutSeconds) {
}
