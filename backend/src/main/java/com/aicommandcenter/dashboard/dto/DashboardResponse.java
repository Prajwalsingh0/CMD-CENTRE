package com.aicommandcenter.dashboard.dto;

import com.aicommandcenter.ai.activity.AiActivityResponse;
import com.aicommandcenter.document.dto.DocumentResponse;
import com.aicommandcenter.goal.dto.GoalResponse;
import com.aicommandcenter.notification.dto.NotificationResponse;
import com.aicommandcenter.task.dto.TaskResponse;

import java.time.Instant;
import java.util.List;

/**
 * One round trip for the whole command centre. Aggregating server-side keeps the dashboard
 * consistent — every panel is rendered from the same instant — and avoids six parallel calls
 * from the browser.
 */
public record DashboardResponse(
        String displayName,
        List<TaskResponse> todayTasks,
        List<TaskResponse> overdueTasks,
        List<TaskResponse> upcomingTasks,
        List<GoalResponse> activeGoals,
        List<Deadline> upcomingDeadlines,
        List<DocumentResponse> recentDocuments,
        List<AiActivityResponse> recentAiActivity,
        List<JobSummary> recentJobAnalyses,
        List<NotificationResponse> unreadNotifications,
        long unreadNotificationCount,
        ProductivityStats stats) {

    public record Deadline(String kind, Long refId, String title, String date, long daysUntil) {
    }

    public record JobSummary(Long id, String jobTitle, String company, int matchScore, Instant createdAt) {
    }

    public record ProductivityStats(
            long totalTasks,
            long completedTasks,
            long openTasks,
            long overdueTasks,
            long activeGoals,
            int completionRate,
            long completedLast7Days,
            long documentsIndexed,
            long aiActionsLast7Days) {
    }
}
