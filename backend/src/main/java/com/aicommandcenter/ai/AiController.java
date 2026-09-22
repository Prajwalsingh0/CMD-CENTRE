package com.aicommandcenter.ai;

import com.aicommandcenter.ai.activity.AiActivityResponse;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.ai.chat.AiChatService;
import com.aicommandcenter.ai.chat.ChatRequest;
import com.aicommandcenter.ai.chat.ChatResponse;
import com.aicommandcenter.ai.command.AiCommandService;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandResultResponse;
import com.aicommandcenter.ai.command.CommandToolRegistry;
import com.aicommandcenter.ai.command.HeuristicCommandParser;
import com.aicommandcenter.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiCommandService commandService;
    private final AiChatService chatService;
    private final AiActivityService activityService;
    private final AiService aiService;
    private final CommandToolRegistry registry;

    public AiController(AiCommandService commandService,
                        AiChatService chatService,
                        AiActivityService activityService,
                        AiService aiService,
                        CommandToolRegistry registry) {
        this.commandService = commandService;
        this.chatService = chatService;
        this.activityService = activityService;
        this.aiService = aiService;
        this.registry = registry;
    }

    @PostMapping("/command")
    public ResponseEntity<CommandResultResponse> command(@Valid @RequestBody CommandRequest request) {
        return ResponseEntity.ok(commandService.execute(SecurityUtils.currentUserId(), request.command()));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<AiActivityResponse>> activity(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(activityService.list(SecurityUtils.currentUserId(), limit));
    }

    @DeleteMapping("/activity")
    public ResponseEntity<Void> clearActivity() {
        activityService.clear(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    /** Lets the UI render honest capability labels instead of promising what is not configured. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", aiService.providerName());
        body.put("remote", aiService.isRemote());
        body.put("supportsStructuredOutput", aiService.supportsStructuredOutput());
        body.put("embeddingDimensions", aiService.embeddingDimensions());
        body.put("supportedIntents", registry.supportedIntents().stream().map(Enum::name).sorted().toList());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/intents")
    public ResponseEntity<List<Map<String, String>>> intents() {
        List<Map<String, String>> body = CommandIntent.executable().stream()
                .map(intent -> Map.of("name", intent.name(), "description", intent.description(),
                        "arguments", String.join(", ", intent.arguments())))
                .toList();
        return ResponseEntity.ok(body);
    }

    public record CommandRequest(@jakarta.validation.constraints.NotBlank
                                 @jakarta.validation.constraints.Size(max = 2000) String command) {
    }

    /** Keeps the example list in one place so the UI hint text cannot drift from the parser. */
    @GetMapping("/examples")
    public ResponseEntity<List<String>> examples() {
        return ResponseEntity.ok(HeuristicCommandParser.supportedExamples());
    }
}
