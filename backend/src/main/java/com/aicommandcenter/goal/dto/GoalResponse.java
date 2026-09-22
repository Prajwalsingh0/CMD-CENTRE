package com.aicommandcenter.goal.dto;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.goal.entity.GoalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GoalResponse(
        Long id,
        String title,
        String description,
        LocalDate deadline,
        Priority priority,
        GoalStatus status,
        String notes,
        long totalTasks,
        long completedTasks,
        int progressPercent,
        boolean overdue,
        List<String> openTaskTitles,
        Instant createdAt,
        Instant updatedAt) {
}
