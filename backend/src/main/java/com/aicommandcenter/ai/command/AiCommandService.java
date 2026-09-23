package com.aicommandcenter.ai.command;

import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.activity.AiActivityResponse;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.exception.ApiException;
import com.aicommandcenter.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates one AI command: parse → resolve tool → validate → execute → record.
 *
 * <p>Failure policy, which is the interesting part:</p>
 * <ul>
 *   <li>An unusable parse or a bad argument is reported as {@code REJECTED} with a readable reason
 *       and an activity row — the user gets feedback instead of a stack trace, and nothing was
 *       written to the database.</li>
 *   <li>A genuine domain failure (resource not found, conflict) is recorded as {@code FAILED} and
 *       then propagated, so the HTTP status stays truthful.</li>
 * </ul>
 *
 * <p>The tool call itself runs inside a programmatic transaction. That is what makes "REJECTED means
 * nothing was written" a structural guarantee rather than an accident of statement ordering: a
 * multi-write tool such as the plan generator commits all of its rows or none of them, while the
 * activity row is written afterwards, outside that transaction, so the audit trail survives a
 * rollback.</p>
 */
@Service
public class AiCommandService {

    private static final Logger log = LoggerFactory.getLogger(AiCommandService.class);
    private static final int MAX_COMMAND_CHARS = 600;

    private final HeuristicCommandParser heuristicParser;
    private final LlmCommandParser llmParser;
    private final CommandToolRegistry registry;
    private final AiActivityService activityService;
    private final AiService aiService;
    private final TransactionTemplate transactionTemplate;

    public AiCommandService(HeuristicCommandParser heuristicParser,
                            LlmCommandParser llmParser,
                            CommandToolRegistry registry,
                            AiActivityService activityService,
                            AiService aiService,
                            TransactionTemplate transactionTemplate) {
        this.heuristicParser = heuristicParser;
        this.llmParser = llmParser;
        this.registry = registry;
        this.activityService = activityService;
        this.aiService = aiService;
        this.transactionTemplate = transactionTemplate;
    }

    public CommandResultResponse execute(Long userId, String command) {
        if (command == null || command.isBlank()) {
            throw new BadRequestException("EMPTY_COMMAND", "Enter a command");
        }
        String trimmed = command.trim();

        ParsedCommand parsed = parse(trimmed);
        if (!parsed.isExecutable()) {
            return recordRejected(userId, trimmed, parsed, parsed.rationale());
        }

        CommandTool tool = registry.find(parsed.intent()).orElse(null);
        if (tool == null) {
            log.warn("Intent {} has no registered tool; rejecting", parsed.intent());
            return recordRejected(userId, trimmed, parsed,
                    "The action '" + parsed.intent() + "' is not available in this deployment.");
        }

        try {
            CommandArgs args = new CommandArgs(parsed.arguments());
            args.rejectUnknown(tool.allowedArguments());
            // Argument validation happens outside the transaction: a bad argument must not open one.
            ToolResult result = transactionTemplate.execute(status -> tool.execute(userId, args));
            if (result == null) {
                throw new IllegalStateException("Command tool " + tool.intent() + " produced no result");
            }
            AiActivityResponse activity = activityService.record(userId, trimmed, parsed.intent().name(),
                    AiActivityService.ActivityStatus.SUCCESS, result.summary());
            return new CommandResultResponse(activity.id(), trimmed, parsed.intent().name(), "SUCCESS",
                    result.summary(), result.steps(), result.data(), parsed.source(), parsed.rationale(),
                    aiService.providerName());
        } catch (BadRequestException ex) {
            // Argument-level problem: the tool refused to act. Nothing was written.
            return recordRejected(userId, trimmed, parsed, ex.getMessage());
        } catch (ApiException ex) {
            activityService.record(userId, trimmed, parsed.intent().name(),
                    AiActivityService.ActivityStatus.FAILED, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Command execution failed for intent {}", parsed.intent(), ex);
            activityService.record(userId, trimmed, parsed.intent().name(),
                    AiActivityService.ActivityStatus.FAILED, "Unexpected failure while executing the command");
            throw ex;
        }
    }

    /** LLM first when the provider can do structured output, deterministic parser otherwise. */
    public ParsedCommand parse(String command) {
        return llmParser.parse(command)
                .filter(ParsedCommand::isExecutable)
                .orElseGet(() -> heuristicParser.parse(command));
    }

    private CommandResultResponse recordRejected(Long userId, String command, ParsedCommand parsed, String reason) {
        String message = reason == null || reason.isBlank()
                ? "The command could not be mapped to a supported action"
                : reason;
        AiActivityResponse activity = activityService.record(userId, truncate(command), parsed.intent().name(),
                AiActivityService.ActivityStatus.REJECTED, message);
        return new CommandResultResponse(activity.id(), command, parsed.intent().name(), "REJECTED", message,
                List.of("No action was taken"), Map.of(), parsed.source(), parsed.rationale(),
                aiService.providerName());
    }

    private static String truncate(String value) {
        return value.length() <= MAX_COMMAND_CHARS ? value : value.substring(0, MAX_COMMAND_CHARS);
    }
}
