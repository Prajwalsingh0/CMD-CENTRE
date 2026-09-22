package com.aicommandcenter.task.dto;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.task.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        Long goalId,
        String goalTitle,
        List<String> tags,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
