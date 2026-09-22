package com.aicommandcenter.analytics.dto;

import java.util.List;

/**
 * Read model for the analytics page. Every number here is computed from the caller's rows —
 * there is no seeded or synthetic value anywhere in this response.
 */
public record AnalyticsResponse(
        int days,
        long totalTasks,
        long completedTasks,
        long openTasks,
        long overdueTasks,
        long cancelledTasks,
        int completionRate,
        long activeGoals,
        long completedGoals,
        long documents,
        long indexedChunks,
        long jobAnalyses,
        long researchReports,
        long aiActions,
        List<DayPoint> tasksCompleted,
        List<DayPoint> tasksCreated,
        List<DayPoint> aiActivity,
        List<GoalProgress> goalProgress,
        List<LabelCount> tasksByStatus,
        List<LabelCount> tasksByPriority) {

    public record DayPoint(String date, long count) {
    }

    public record GoalProgress(Long id, String title, String status, int progressPercent,
                               long totalTasks, long completedTasks, boolean overdue) {
    }

    public record LabelCount(String label, long count) {
    }
}
