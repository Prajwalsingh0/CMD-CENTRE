package com.aicommandcenter.ai.command;

import com.aicommandcenter.ai.activity.AiActivityRepository;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The security-critical behaviour of the command pipeline, isolated from the web layer.
 *
 * <p>The key assertions are negative ones: when a parse is unknown, when an argument is not
 * declared, or when a tool refuses, the tool must not run and nothing must be persisted.</p>
 */
class AiCommandServiceTest {

    private HeuristicCommandParser heuristicParser;
    private LlmCommandParser llmParser;
    private AiActivityService activityService;
    private AiActivityRepository activityRepository;
    private CommandToolRegistry registry;
    private com.aicommandcenter.ai.AiService aiService;
    private TrackingTool tool;
    private AiCommandService service;

    /** Test double that records whether it was invoked. */
    private static final class TrackingTool implements CommandTool {
        private int calls;
        private RuntimeException toThrow;

        @Override
        public CommandIntent intent() {
            return CommandIntent.CREATE_TASK;
        }

        @Override
        public Set<String> allowedArguments() {
            return Set.of("title", "dueDate");
        }

        @Override
        public ToolResult execute(Long userId, CommandArgs args) {
            // Mirrors what a real tool does: it validates its own declared arguments first, so a
            // rejected argument never counts as an execution.
            args.require("title", 180);
            args.optionalDate("dueDate");
            calls++;
            if (toThrow != null) {
                throw toThrow;
            }
            return ToolResult.of("did the thing", List.of("did the thing"));
        }
    }

    @BeforeEach
    void setUp() {
        heuristicParser = new HeuristicCommandParser();
        llmParser = Mockito.mock(LlmCommandParser.class);
        activityRepository = Mockito.mock(AiActivityRepository.class);
        activityService = Mockito.mock(AiActivityService.class);
        aiService = Mockito.mock(com.aicommandcenter.ai.AiService.class);
        tool = new TrackingTool();
        registry = new CommandToolRegistry(List.of(tool));
        when(aiService.providerName()).thenReturn("local");
        when(activityService.record(anyLong(), anyString(), anyString(), any(),
                ArgumentMatchers.anyString()))
                .thenReturn(new com.aicommandcenter.ai.activity.AiActivityResponse(
                        7L, "cmd", "CREATE_TASK", "SUCCESS", "summary", java.time.Instant.now()));
        service = new AiCommandService(heuristicParser, llmParser, registry, activityService, aiService);
    }

    @Test
    @DisplayName("the deterministic parser drives execution when no LLM is configured")
    void heuristicPathExecutes() {
        when(llmParser.parse(anyString())).thenReturn(Optional.empty());

        CommandResultResponse response = service.execute(1L, "create a task to revise SQL");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.intent()).isEqualTo("CREATE_TASK");
        assertThat(tool.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown parse is rejected and the tool is never invoked")
    void unknownIntentNeverReachesATool() {
        when(llmParser.parse(anyString())).thenReturn(Optional.empty());

        CommandResultResponse response = service.execute(1L, "make me a sandwich");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.intent()).isEqualTo("UNKNOWN");
        assertThat(tool.calls).isZero();
        verify(activityService).record(anyLong(), anyString(), anyString(),
                ArgumentMatchers.eq(AiActivityService.ActivityStatus.REJECTED), anyString());
    }

    @Test
    @DisplayName("a model-supplied argument the tool does not declare is rejected before execution")
    void undeclaredArgumentsAreRefused() {
        when(llmParser.parse(anyString())).thenReturn(Optional.of(new ParsedCommand(
                CommandIntent.CREATE_TASK,
                Map.of("title", "Legit task", "sql", "DROP TABLE users"),
                "test", ParsedCommand.SOURCE_LLM)));

        CommandResultResponse response = service.execute(1L, "drop the users table");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.summary()).contains("sql");
        assertThat(tool.calls).isZero();
    }

    @Test
    @DisplayName("an out-of-range date is rejected with the offending value named")
    void invalidDatesAreRefused() {
        when(llmParser.parse(anyString())).thenReturn(Optional.of(new ParsedCommand(
                CommandIntent.CREATE_TASK,
                Map.of("title", "Task", "dueDate", "next tuesday-ish"),
                "test", ParsedCommand.SOURCE_LLM)));

        CommandResultResponse response = service.execute(1L, "create a task");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.summary()).contains("dueDate");
        assertThat(tool.calls).isZero();
    }

    @Test
    @DisplayName("a domain failure is recorded as FAILED and still surfaces as a real error")
    void domainFailuresPropagate() {
        tool.toThrow = new ResourceNotFoundException("Task", 99L);
        when(llmParser.parse(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(1L, "create a task to redeploy the service"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(activityService).record(anyLong(), anyString(), anyString(),
                ArgumentMatchers.eq(AiActivityService.ActivityStatus.FAILED), anyString());
    }

    @Test
    @DisplayName("the registry refuses to expose a tool for UNKNOWN")
    void unknownCanNeverBeExecutable() {
        assertThat(registry.supports(CommandIntent.UNKNOWN)).isFalse();
        assertThat(registry.find(CommandIntent.UNKNOWN)).isEmpty();
        assertThat(registry.supportedIntents()).contains(CommandIntent.CREATE_TASK);
    }

    @Test
    @DisplayName("a bad argument is reported as REJECTED rather than a server error")
    void badArgumentsAreUserFacing() {
        tool.toThrow = new BadRequestException("MISSING_ARGUMENT", "'title' is required for this command");
        when(llmParser.parse(anyString())).thenReturn(Optional.empty());

        CommandResultResponse response = service.execute(1L, "create a task to redeploy the service");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.summary()).contains("title");
        verify(activityService, never()).record(anyLong(), anyString(), anyString(),
                ArgumentMatchers.eq(AiActivityService.ActivityStatus.FAILED), anyString());
    }
}
