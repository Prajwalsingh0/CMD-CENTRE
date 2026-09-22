package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.goal.dto.GoalRequest;
import com.aicommandcenter.goal.dto.GoalResponse;
import com.aicommandcenter.goal.entity.GoalStatus;
import com.aicommandcenter.goal.service.GoalService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CreateGoalTool implements CommandTool {

    private final GoalService goalService;

    public CreateGoalTool(GoalService goalService) {
        this.goalService = goalService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.CREATE_GOAL;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.CREATE_GOAL.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        String title = args.require("title", 180);
        GoalRequest request = new GoalRequest(title, args.optional("description"), args.optionalDate("deadline"),
                args.optionalPriority("priority"), GoalStatus.ACTIVE, null);
        GoalResponse goal = goalService.create(userId, request);

        List<String> steps = List.of(
                "Created goal \"" + goal.title() + "\"",
                goal.deadline() == null ? "No target date set" : "Target date " + goal.deadline());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goal", goal);
        return ToolResult.of("Created goal \"" + goal.title() + "\"", steps, data);
    }
}
