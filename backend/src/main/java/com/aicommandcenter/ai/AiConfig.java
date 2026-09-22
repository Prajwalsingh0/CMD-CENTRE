package com.aicommandcenter.ai;

import com.aicommandcenter.ai.local.LocalAiService;
import com.aicommandcenter.ai.openai.OpenAiService;
import com.aicommandcenter.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * The only place in the codebase that knows which {@link AiService} is live.
 *
 * <p>Misconfiguration fails the startup rather than the first user request: asking for a remote
 * provider without a key, or naming a provider that does not exist, is a deployment error and
 * should be impossible to miss.</p>
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiService aiService(AiProperties properties, ObjectMapper objectMapper) {
        String provider = properties.provider() == null
                ? LocalAiService.NAME
                : properties.provider().trim().toLowerCase(Locale.ROOT);

        AiService service = switch (provider) {
            case LocalAiService.NAME -> new LocalAiService(properties.embeddingDimensions());
            case OpenAiService.NAME -> {
                AiProperties.OpenAi openAi = properties.openAi();
                if (openAi == null || openAi.apiKey() == null || openAi.apiKey().isBlank()) {
                    throw new IllegalStateException(
                            "AI_PROVIDER=openai requires AI_API_KEY. Either provide the key or use AI_PROVIDER=local.");
                }
                yield new OpenAiService(openAi, objectMapper);
            }
            default -> throw new IllegalStateException("Unknown app.ai.provider '" + provider
                    + "'. Supported values: local, openai.");
        };

        log.info("AI provider active: {} (remote={}, structuredOutput={})",
                service.providerName(), service.isRemote(), service.supportsStructuredOutput());
        return service;
    }
}
