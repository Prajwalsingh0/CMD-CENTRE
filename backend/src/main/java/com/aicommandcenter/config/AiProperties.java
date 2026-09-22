package com.aicommandcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, OpenAi openAi, int embeddingDimensions) {

    public record OpenAi(String baseUrl, String apiKey, String model, int timeoutSeconds) {
    }

    public boolean isOpenAiConfigured() {
        return "openai".equalsIgnoreCase(provider)
                && openAi != null
                && openAi.apiKey() != null
                && !openAi.apiKey().isBlank();
    }
}
