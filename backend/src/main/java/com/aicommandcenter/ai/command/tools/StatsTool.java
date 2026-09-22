package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.analytics.dto.AnalyticsResponse;
import com.aicommandcenter.analytics.service.AnalyticsService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StatsTool implements CommandTool {

    private final AnalyticsService analyticsService;

    public StatsTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.SHOW_STATS;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.SHOW_STATS.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        AnalyticsResponse stats = analyticsService.build(userId, 30);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analytics", stats);
        return ToolResult.of("Completion rate " + stats.completionRate() + "%",
                List.of(stats.totalTasks() + " task(s) total, " + stats.openTasks() + " still open",
                        stats.completedTasks() + " completed, " + stats.overdueTasks() + " overdue",
                        stats.activeGoals() + " active goal(s), " + stats.completedGoals() + " completed",
                        stats.documents() + " document(s) indexed into " + stats.indexedChunks() + " passage(s)",
                        stats.aiActions() + " AI action(s) recorded"),
                data);
    }
}
