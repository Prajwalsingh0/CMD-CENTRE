package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.rag.RagService;
import com.aicommandcenter.rag.dto.AnswerResponse;
import com.aicommandcenter.rag.dto.AnswerSource;
import com.aicommandcenter.rag.dto.KnowledgeAskRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AskKnowledgeBaseTool implements CommandTool {

    private final RagService ragService;

    public AskKnowledgeBaseTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.ASK_KNOWLEDGE_BASE;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.ASK_KNOWLEDGE_BASE.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        String question = args.require("question", 1000);
        AnswerResponse answer = ragService.ask(userId, new KnowledgeAskRequest(question, null, null));

        List<String> steps = new ArrayList<>();
        steps.add("Searched your knowledge base");
        if (answer.grounded()) {
            for (AnswerSource source : answer.sources()) {
                steps.add("Used " + source.documentName() + " (part " + (source.chunkIndex() + 1) + ")");
            }
        } else {
            steps.add("No matching passage found in your documents");
        }
        steps.add("Answered with the " + answer.provider() + " provider");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer.answer());
        data.put("sources", answer.sources());
        data.put("grounded", answer.grounded());
        return ToolResult.of(answer.grounded()
                ? "Answered from " + answer.sources().size() + " passage(s) in your documents"
                : "Could not find an answer in your documents", steps, data);
    }
}
