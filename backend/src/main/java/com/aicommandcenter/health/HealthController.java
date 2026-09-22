package com.aicommandcenter.health;

import com.aicommandcenter.ai.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unauthenticated liveness probe. Deliberately exposes nothing but a status, a timestamp,
 * the active profile and the active AI provider name — no configuration values, no versions
 * of dependencies, no environment detail.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final AiService aiService;
    private final String applicationName;

    public HealthController(AiService aiService,
                            @Value("${spring.application.name:ai-command-center}") String applicationName) {
        this.aiService = aiService;
        this.applicationName = applicationName;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", applicationName);
        body.put("aiProvider", aiService.providerName());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }
}
