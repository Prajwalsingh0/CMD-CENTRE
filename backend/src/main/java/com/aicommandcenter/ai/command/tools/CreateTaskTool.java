package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.goal.service.GoalService;
import com.aicommandcenter.task.dto.TaskRequest;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CreateTaskTool implements CommandTool {

    private final TaskService taskService;
    private final GoalService goalService;

    public CreateTaskTool(TaskService taskService, GoalService goalService) {
        this.taskService = taskService;
        this.goalService = goalService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.CREATE_TASK;
    }

    @Override
    public java.util.Set<String> allowedArguments() {
        return CommandIntent.CREATE_TASK.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        String title = args.require("title", 180);

        Long goalId = null;
        String goalTitle = args.optional("goalTitle");
        if (goalTitle != null) {
            goalId = goalService.findIdByTitle(userId, goalTitle);
            if (goalId == null) {
                throw new BadRequestException("GOAL_NOT_FOUND", "No goal matches '" + goalTitle + "'");
            }
        }

        TaskRequest request = new TaskRequest(
                title,
                args.optional("description"),
                TaskStatus.TODO,
                args.optionalPriority("priority"),
                args.optionalDate("dueDate"),
                goalId,
                args.optionalList("tags"));
        TaskResponse task = taskService.create(userId, request);

        List<String> steps = new ArrayList<>();
        steps.add("Created task \"" + task.title() + "\"");
        if (task.dueDate() != null) {
            steps.add("Due " + task.dueDate());
        }
        if (task.goalTitle() != null) {
            steps.add("Linked to goal \"" + task.goalTitle() + "\"");
        }
        steps.add("Priority " + task.priority());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", task);
        return ToolResult.of("Created task \"" + task.title() + "\"", steps, data);
    }
}
