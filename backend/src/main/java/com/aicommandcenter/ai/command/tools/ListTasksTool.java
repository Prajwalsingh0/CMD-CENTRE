package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.common.PageResponse;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ListTasksTool implements CommandTool {

    private final TaskService taskService;

    public ListTasksTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.LIST_TASKS;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.LIST_TASKS.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        TaskStatus status = args.optionalStatus("status");
        int limit = args.optionalInt("limit", 10, 1, 50);
        PageResponse<TaskResponse> page = taskService.list(userId, status, null, args.optionalPriority("priority"),
                null, args.optional("search"), null, false, 0, limit, null);

        List<String> steps = new ArrayList<>();
        for (TaskResponse task : page.items()) {
            steps.add(task.title() + (task.dueDate() == null ? "" : " (due " + task.dueDate() + ")")
                    + (task.overdue() ? " — OVERDUE" : ""));
        }
        if (steps.isEmpty()) {
            steps.add("No tasks matched.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", page.items());
        data.put("totalItems", page.totalItems());
        return ToolResult.of("Found " + page.totalItems() + " task(s); showing " + page.items().size(), steps, data);
    }
}
