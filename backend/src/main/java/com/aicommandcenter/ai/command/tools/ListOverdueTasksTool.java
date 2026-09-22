package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ListOverdueTasksTool implements CommandTool {

    private final TaskService taskService;

    public ListOverdueTasksTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.LIST_OVERDUE_TASKS;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.LIST_OVERDUE_TASKS.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        List<TaskResponse> overdue = taskService.overdue(userId);
        List<String> steps = new ArrayList<>();
        overdue.forEach(task -> steps.add(task.title() + " (due " + task.dueDate() + ")"));
        if (steps.isEmpty()) {
            steps.add("Nothing is overdue.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", overdue);
        return ToolResult.of(overdue.isEmpty() ? "No overdue tasks" : overdue.size() + " overdue task(s)", steps, data);
    }
}
