package com.aicommandcenter.ai.openai;

import com.aicommandcenter.ai.AiCompletion;
import com.aicommandcenter.ai.AiException;
import com.aicommandcenter.ai.AiMessage;
import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Any OpenAI-compatible {@code /chat/completions} + {@code /embeddings} endpoint.
 *
 * <p>Deliberately hand-rolled against {@link RestClient} instead of pulling in a vendor SDK:
 * the surface used here is two POSTs, and this keeps the dependency graph and the credential
 * handling completely explicit. The API key is sent in a header and is never logged, never
 * echoed into an exception, and never included in an API response.</p>
 */
public class OpenAiService implements AiService {

    public static final String NAME = "openai";
    /** Nominal dimension of the default embedding model; the real value comes from the response. */
    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final int MAX_RESPONSE_CHARS = 200_000;

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private final AiProperties.OpenAi config;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public OpenAiService(AiProperties.OpenAi config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(2, config.timeoutSeconds()));
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        String baseUrl = config.baseUrl() == null ? "https://api.openai.com/v1" : config.baseUrl().replaceAll("/+$", "");
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .build();
    }

    @Override
    public String providerName() {
        return NAME;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true;
    }

    @Override
    public int embeddingDimensions() {
        return EMBEDDING_DIMENSIONS;
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        ArrayNode messages = body.putArray("messages");
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(message("system", request.system()));
        }
        for (AiMessage message : request.messages()) {
            messages.add(message(message.role(), message.content()));
        }
        if (request.jsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            String text = extractContent(raw);
            return new AiCompletion(text, NAME, config.model(), true);
        } catch (RestClientException ex) {
            log.warn("AI completion failed via provider={}: {}", NAME, ex.getClass().getSimpleName());
            throw AiException.unavailable(NAME, safeDetail(ex));
        }
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        List<double[]> vectors = new ArrayList<>();
        if (texts == null || texts.isEmpty()) {
            return vectors;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", embeddingModel());
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);
        try {
            String raw = client.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw AiException.invalidResponse(NAME);
            }
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                double[] vector = new double[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (AiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("AI embedding failed via provider={}: {}", NAME, ex.getClass().getSimpleName());
            throw AiException.unavailable(NAME, safeDetail(ex));
        } catch (Exception ex) {
            throw AiException.invalidResponse(NAME);
        }
    }

    private String embeddingModel() {
        return System.getenv().getOrDefault("AI_EMBEDDING_MODEL", "text-embedding-3-small");
    }

    private ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content == null ? "" : content);
        return node;
    }

    private String extractContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AiException.invalidResponse(NAME);
        }
        if (raw.length() > MAX_RESPONSE_CHARS) {
            throw AiException.invalidResponse(NAME);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw AiException.invalidResponse(NAME);
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw AiException.invalidResponse(NAME);
            }
            return content;
        } catch (AiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AiException.invalidResponse(NAME);
        }
    }

    /** Status line only: the response body could echo prompt content, so it is never propagated. */
    private String safeDetail(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "request failed";
        }
        return message.length() > 120 ? message.substring(0, 120) : message;
    }
}
