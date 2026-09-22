package com.aicommandcenter.analytics.service;

import com.aicommandcenter.ai.activity.AiActivity;
import com.aicommandcenter.ai.activity.AiActivityRepository;
import com.aicommandcenter.analytics.dto.AnalyticsResponse;
import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.document.entity.DocumentStatus;
import com.aicommandcenter.document.repository.DocumentChunkRepository;
import com.aicommandcenter.document.repository.DocumentRepository;
import com.aicommandcenter.goal.entity.Goal;
import com.aicommandcenter.goal.entity.GoalStatus;
import com.aicommandcenter.goal.repository.GoalRepository;
import com.aicommandcenter.job.repository.JobAnalysisRepository;
import com.aicommandcenter.research.repository.ResearchReportRepository;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics over the caller's real rows.
 *
 * <p>Rows are loaded once and aggregated in memory rather than issuing a dozen grouped queries.
 * For a personal workspace that is the right trade-off — and it is the reason every figure on the
 * analytics page matches the underlying lists exactly.</p>
 */
@Service
public class AnalyticsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final JobAnalysisRepository jobRepository;
    private final ResearchReportRepository researchRepository;
    private final AiActivityRepository activityRepository;

    public AnalyticsService(TaskRepository taskRepository,
                            GoalRepository goalRepository,
                            DocumentRepository documentRepository,
                            DocumentChunkRepository chunkRepository,
                            JobAnalysisRepository jobRepository,
                            ResearchReportRepository researchRepository,
                            AiActivityRepository activityRepository) {
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.jobRepository = jobRepository;
        this.researchRepository = researchRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse build(Long userId, int days) {
        int window = Math.min(Math.max(1, days), 180);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(window - 1L);

        List<Task> tasks = taskRepository.findAllByUserId(userId);
        List<Goal> goals = goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<AiActivity> activities = activityRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
        long cancelled = tasks.stream().filter(task -> task.getStatus() == TaskStatus.CANCELLED).count();
        long overdue = tasks.stream().filter(task -> isOverdue(task, today)).count();
        long open = tasks.stream().filter(task -> task.getStatus() != null && task.getStatus().isOpen()).count();
        long countable = tasks.size() - cancelled;

        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Task task : tasks) {
            if (task.getStatus() != null) {
                byStatus.merge(task.getStatus(), 1L, Long::sum);
            }
            if (task.getPriority() != null) {
                byPriority.merge(task.getPriority(), 1L, Long::sum);
            }
        }

        return new AnalyticsResponse(
                window,
                tasks.size(),
                completed,
                open,
                overdue,
                cancelled,
                countable <= 0 ? 0 : (int) Math.round(completed * 100.0 / countable),
                goals.stream().filter(goal -> goal.getStatus() == GoalStatus.ACTIVE).count(),
                goals.stream().filter(goal -> goal.getStatus() == GoalStatus.COMPLETED).count(),
                documentRepository.countByUserId(userId),
                chunkRepository.countByUserId(userId),
                jobRepository.countByUserId(userId),
                researchRepository.countByUserId(userId),
                activities.size(),
                series(tasks, activities, from, today, true),
                series(tasks, activities, from, today, false),
                activitySeries(activities, from, today),
                goalProgress(userId, goals, tasks, today),
                byStatus.entrySet().stream()
                        .map(entry -> new AnalyticsResponse.LabelCount(entry.getKey().name(), entry.getValue()))
                        .toList(),
                byPriority.entrySet().stream()
                        .map(entry -> new AnalyticsResponse.LabelCount(entry.getKey().name(), entry.getValue()))
                        .toList());
    }

    private List<AnalyticsResponse.DayPoint> series(List<Task> tasks, List<AiActivity> activities,
                                                     LocalDate from, LocalDate to, boolean completed) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            counts.put(day, 0L);
        }
        for (Task task : tasks) {
            Instant moment = completed ? task.getCompletedAt() : task.getCreatedAt();
            if (moment == null) {
                continue;
            }
            LocalDate day = moment.atZone(ZONE).toLocalDate();
            counts.computeIfPresent(day, (key, value) -> value + 1);
        }
        return counts.entrySet().stream()
                .map(entry -> new AnalyticsResponse.DayPoint(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<AnalyticsResponse.DayPoint> activitySeries(List<AiActivity> activities, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            counts.put(day, 0L);
        }
        for (AiActivity activity : activities) {
            if (activity.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = activity.getCreatedAt().atZone(ZONE).toLocalDate();
            counts.computeIfPresent(day, (key, value) -> value + 1);
        }
        return counts.entrySet().stream()
                .map(entry -> new AnalyticsResponse.DayPoint(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<AnalyticsResponse.GoalProgress> goalProgress(Long userId, List<Goal> goals, List<Task> allTasks,
                                                              LocalDate today) {
        List<AnalyticsResponse.GoalProgress> progress = new ArrayList<>();
        for (Goal goal : goals) {
            List<Task> tasks = allTasks.stream()
                    .filter(task -> goal.getId().equals(task.getGoalId()))
                    .toList();
            long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
            long cancelled = tasks.stream().filter(task -> task.getStatus() == TaskStatus.CANCELLED).count();
            long countable = tasks.size() - cancelled;
            int percent = countable <= 0 ? 0 : (int) Math.round(completed * 100.0 / countable);
            boolean overdue = goal.getDeadline() != null && goal.getDeadline().isBefore(today)
                    && goal.getStatus() == GoalStatus.ACTIVE;
            progress.add(new AnalyticsResponse.GoalProgress(goal.getId(), goal.getTitle(), goal.getStatus().name(),
                    percent, tasks.size(), completed, overdue));
        }
        return progress;
    }

    private boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null && task.getDueDate().isBefore(today)
                && task.getStatus() != null && task.getStatus().isOpen();
    }

    /** Used by the dashboard for the "indexed documents" figure. */
    @Transactional(readOnly = true)
    public long indexedDocuments(Long userId) {
        return documentRepository.findAllByUserIdAndStatus(userId, DocumentStatus.READY).size();
    }
}
