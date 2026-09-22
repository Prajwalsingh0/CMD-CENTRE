package com.aicommandcenter.goal.service;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.goal.dto.GoalRequest;
import com.aicommandcenter.goal.dto.GoalResponse;
import com.aicommandcenter.goal.entity.Goal;
import com.aicommandcenter.goal.entity.GoalStatus;
import com.aicommandcenter.goal.repository.GoalRepository;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class GoalService {

    /** Tasks that should be auto-completed when every task of a goal is done. */
    private static final List<TaskStatus> OPEN_STATUSES = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);

    private final GoalRepository goalRepository;
    private final TaskRepository taskRepository;

    public GoalService(GoalRepository goalRepository, TaskRepository taskRepository) {
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("VALIDATION_FAILED", "title is required");
        }
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setTitle(request.title().trim());
        goal.setDescription(trimToNull(request.description()));
        goal.setDeadline(request.deadline());
        goal.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        goal.setStatus(request.status() == null ? GoalStatus.ACTIVE : request.status());
        goal.setNotes(trimToNull(request.notes()));
        return toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long goalId) {
        return toResponse(require(userId, goalId));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId, GoalStatus status) {
        List<Goal> goals = status == null
                ? goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                : goalRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        return goals.stream().map(this::toResponse).toList();
    }

    @Transactional
    public GoalResponse update(Long userId, Long goalId, GoalRequest request) {
        Goal goal = require(userId, goalId);
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("VALIDATION_FAILED", "title must not be blank");
            }
            goal.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            goal.setDescription(trimToNull(request.description()));
        }
        if (request.deadline() != null) {
            goal.setDeadline(request.deadline());
        }
        if (request.priority() != null) {
            goal.setPriority(request.priority());
        }
        if (request.status() != null) {
            goal.setStatus(request.status());
        }
        if (request.notes() != null) {
            goal.setNotes(trimToNull(request.notes()));
        }
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void delete(Long userId, Long goalId) {
        Goal goal = require(userId, goalId);
        // Detach tasks instead of cascading the delete so the user never loses work silently.
        List<Task> tasks = taskRepository.findAllByUserIdAndGoalId(userId, goalId);
        tasks.forEach(task -> task.setGoalId(null));
        taskRepository.saveAll(tasks);
        goalRepository.delete(goal);
    }

    @Transactional
    public GoalResponse refreshStatus(Long userId, Long goalId) {
        Goal goal = require(userId, goalId);
        List<Task> tasks = taskRepository.findAllByUserIdAndGoalId(userId, goalId);
        long completed = tasks.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
        long cancelled = tasks.stream().filter(task -> task.getStatus() == TaskStatus.CANCELLED).count();
        // Cancelled tasks leave the denominator, exactly as they do in progressPercent, so the
        // goal can legitimately reach 100% without its cancelled work blocking closure.
        long countable = tasks.size() - cancelled;
        if (countable > 0 && completed == countable && goal.getStatus() == GoalStatus.ACTIVE) {
            goal.setStatus(GoalStatus.COMPLETED);
        }
        return toResponse(goalRepository.save(goal));
    }

    public Goal require(Long userId, Long goalId) {
        return goalRepository.findById(goalId)
                .filter(goal -> goal.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
    }

    /**
     * Resolves a goal by title for the AI command layer. Returns {@code null} when nothing matches
     * so the calling tool can produce a clear message instead of a stack trace.
     */
    @Transactional(readOnly = true)
    public Long findIdByTitle(Long userId, String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String needle = title.trim();
        return goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(goal -> goal.getTitle().equalsIgnoreCase(needle)
                        || goal.getTitle().toLowerCase().contains(needle.toLowerCase()))
                .map(Goal::getId)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public GoalResponse toResponse(Goal goal) {
        long total = taskRepository.countByUserIdAndGoalId(goal.getUserId(), goal.getId());
        long completed = taskRepository.countByUserIdAndGoalIdAndStatus(goal.getUserId(), goal.getId(), TaskStatus.COMPLETED);
        long cancelled = taskRepository.findAllByUserIdAndGoalId(goal.getUserId(), goal.getId()).stream()
                .filter(task -> task.getStatus() == TaskStatus.CANCELLED)
                .count();
        long countable = total - cancelled;
        int percent = countable <= 0 ? 0 : (int) Math.round((completed * 100.0) / countable);
        List<String> openTitles = taskRepository.findAllByUserIdAndGoalId(goal.getUserId(), goal.getId()).stream()
                .filter(task -> OPEN_STATUSES.contains(task.getStatus()))
                .map(Task::getTitle)
                .limit(5)
                .toList();
        boolean overdue = goal.getDeadline() != null
                && goal.getDeadline().isBefore(LocalDate.now())
                && goal.getStatus() == GoalStatus.ACTIVE;
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getDeadline(),
                goal.getPriority(), goal.getStatus(), goal.getNotes(), total, completed, percent, overdue,
                openTitles, goal.getCreatedAt(), goal.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
