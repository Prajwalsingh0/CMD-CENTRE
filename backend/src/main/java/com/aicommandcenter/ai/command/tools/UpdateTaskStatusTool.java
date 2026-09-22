package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class UpdateTaskStatusTool implements CommandTool {

    private final TaskService taskService;

    public UpdateTaskStatusTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.UPDATE_TASK_STATUS;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.UPDATE_TASK_STATUS.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        TaskStatus status = args.optionalStatus("status") == null ? TaskStatus.COMPLETED : args.optionalStatus("status");

        Long taskId = args.optionalLong("taskId");
        TaskResponse task;
        if (taskId != null) {
            task = taskService.get(userId, taskId);
        } else {
            String title = args.optional("taskTitle");
            if (title == null || title.isBlank()) {
                throw new BadRequestException("MISSING_ARGUMENT",
                        "Name the task to update, or give its id");
            }
            String needle = title.toLowerCase(Locale.ROOT).trim();
            task = taskService.entitiesForUser(userId).stream()
                    .filter(entity -> entity.getTitle().toLowerCase(Locale.ROOT).contains(needle))
                    .findFirst()
                    .map(entity -> taskService.get(userId, entity.getId()))
                    .orElseThrow(() -> new BadRequestException("TASK_NOT_FOUND",
                            "No task matches '" + title + "'"));
        }

        TaskResponse updated = taskService.updateStatus(userId, task.id(), status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", updated);
        return ToolResult.of("Task \"" + updated.title() + "\" is now " + updated.status(),
                List.of("Set \"" + updated.title() + "\" to " + updated.status()), data);
    }
}
