package com.aicommandcenter.dashboard.service;

import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.analytics.dto.AnalyticsResponse;
import com.aicommandcenter.analytics.service.AnalyticsService;
import com.aicommandcenter.dashboard.dto.DashboardResponse;
import com.aicommandcenter.document.service.DocumentService;
import com.aicommandcenter.goal.dto.GoalResponse;
import com.aicommandcenter.goal.entity.GoalStatus;
import com.aicommandcenter.goal.service.GoalService;
import com.aicommandcenter.job.service.JobAnalysisService;
import com.aicommandcenter.notification.service.NotificationService;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.service.TaskService;
import com.aicommandcenter.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Assembles the command-centre dashboard from the owning services. */
@Service
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int UPCOMING_DAYS = 7;

    private final TaskService taskService;
    private final GoalService goalService;
    private final DocumentService documentService;
    private final JobAnalysisService jobAnalysisService;
    private final AiActivityService activityService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final UserService userService;

    public DashboardService(TaskService taskService,
                            GoalService goalService,
                            DocumentService documentService,
                            JobAnalysisService jobAnalysisService,
                            AiActivityService activityService,
                            NotificationService notificationService,
                            AnalyticsService analyticsService,
                            UserService userService) {
        this.taskService = taskService;
        this.goalService = goalService;
        this.documentService = documentService;
        this.jobAnalysisService = jobAnalysisService;
        this.activityService = activityService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @Transactional
    public DashboardResponse build(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        List<Task> tasks = taskService.entitiesForUser(userId);
        List<TaskResponse> taskResponses = taskService.responsesFor(userId, tasks);

        List<TaskResponse> todayTasks = taskResponses.stream()
                .filter(task -> task.status() != null && task.status().isOpen())
                .filter(task -> task.dueDate() != null
                        && (task.dueDate().isEqual(today) || task.dueDate().isBefore(today)))
                .sorted(Comparator.comparing(TaskResponse::dueDate))
                .toList();

        List<TaskResponse> overdue = taskResponses.stream().filter(TaskResponse::overdue).toList();

        List<TaskResponse> upcoming = taskResponses.stream()
                .filter(task -> task.status() != null && task.status().isOpen())
                .filter(task -> task.dueDate() != null
                        && task.dueDate().isAfter(today)
                        && !task.dueDate().isAfter(today.plusDays(UPCOMING_DAYS)))
                .sorted(Comparator.comparing(TaskResponse::dueDate))
                .limit(8)
                .toList();

        List<GoalResponse> activeGoals = goalService.list(userId, GoalStatus.ACTIVE);

        List<DashboardResponse.Deadline> deadlines = new ArrayList<>();
        for (GoalResponse goal : activeGoals) {
            if (goal.deadline() != null) {
                deadlines.add(new DashboardResponse.Deadline("GOAL", goal.id(), goal.title(),
                        goal.deadline().toString(), ChronoUnit.DAYS.between(today, goal.deadline())));
            }
        }
        for (Task task : tasks) {
            if (task.getDueDate() != null && task.getStatus() != null && task.getStatus().isOpen()) {
                deadlines.add(new DashboardResponse.Deadline("TASK", task.getId(), task.getTitle(),
                        task.getDueDate().toString(), ChronoUnit.DAYS.between(today, task.getDueDate())));
            }
        }
        List<DashboardResponse.Deadline> sortedDeadlines = deadlines.stream()
                .filter(deadline -> deadline.daysUntil() >= 0)
                .sorted(Comparator.comparingLong(DashboardResponse.Deadline::daysUntil))
                .limit(6)
                .toList();

        AnalyticsResponse analytics = analyticsService.build(userId, 30);
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long completedLast7Days = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .filter(task -> task.getCompletedAt() != null && task.getCompletedAt().isAfter(sevenDaysAgo))
                .count();

        DashboardResponse.ProductivityStats stats = new DashboardResponse.ProductivityStats(
                analytics.totalTasks(),
                analytics.completedTasks(),
                analytics.openTasks(),
                analytics.overdueTasks(),
                analytics.activeGoals(),
                analytics.completionRate(),
                completedLast7Days,
                analytics.documents(),
                analytics.aiActions());

        return new DashboardResponse(
                userService.profile(userId).displayName(),
                todayTasks.stream().limit(6).toList(),
                overdue.stream().limit(6).toList(),
                upcoming,
                activeGoals.stream().limit(5).toList(),
                sortedDeadlines,
                documentService.recent(userId, 4),
                activityService.recent(userId, 6),
                jobAnalysisService.recent(userId, 3).stream()
                        .map(job -> new DashboardResponse.JobSummary(job.id(), job.jobTitle(), job.company(),
                                job.matchScore(), job.createdAt()))
                        .toList(),
                notificationService.unreadPreview(userId, 5),
                notificationService.unreadCount(userId),
                stats);
    }
}
