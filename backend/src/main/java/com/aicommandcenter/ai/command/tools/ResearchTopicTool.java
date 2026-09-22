package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.research.dto.ResearchRequest;
import com.aicommandcenter.research.dto.ResearchResponse;
import com.aicommandcenter.research.service.ResearchService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ResearchTopicTool implements CommandTool {

    private final ResearchService researchService;

    public ResearchTopicTool(ResearchService researchService) {
        this.researchService = researchService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.RESEARCH_TOPIC;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.RESEARCH_TOPIC.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        String topic = args.require("topic", 240);
        ResearchResponse report = researchService.research(userId,
                new ResearchRequest(topic, args.optional("depth")));

        List<String> steps = report.grounded()
                ? List.of("Searched your document knowledge base",
                        "Cited " + report.sources().size() + " source passage(s)",
                        "Compiled key findings, concepts and recommendations")
                : List.of("No matching passage in your knowledge base",
                        "Produced a report without citations",
                        "See the disclosure on the report for what it is based on");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report", report);
        return ToolResult.of("Research report on \"" + report.topic() + "\" created", steps, data);
    }
}
