package com.aicommandcenter.task.service;

import com.aicommandcenter.common.PageResponse;
import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.goal.repository.GoalRepository;
import com.aicommandcenter.task.dto.TaskRequest;
import com.aicommandcenter.task.dto.TaskResponse;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.repository.TaskRepository;
import com.aicommandcenter.task.repository.TaskSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaskService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "dueDate", "title", "priority", "status");
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;

    public TaskService(TaskRepository taskRepository, GoalRepository goalRepository) {
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
    }

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("VALIDATION_FAILED", "title is required");
        }
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
        task.setGoalId(resolveGoal(userId, request.goalId()));
        task.setTags(joinTags(request.tags()));
        if (task.getStatus() == TaskStatus.COMPLETED) {
            task.setCompletedAt(Instant.now());
        }
        return toResponse(taskRepository.save(task), Map.of());
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long userId, Long taskId) {
        Task task = require(userId, taskId);
        return toResponse(task, goalTitles(userId, List.of(task)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId, TaskStatus status, List<TaskStatus> statuses, Priority priority,
                                           Long goalId, String search, LocalDate dueBefore, boolean overdueOnly,
                                           int page, int size, String sort) {
        Specification<Task> spec = TaskSpecifications.ownedBy(userId);
        spec = and(spec, TaskSpecifications.hasStatus(status));
        spec = and(spec, TaskSpecifications.hasStatuses(statuses));
        spec = and(spec, TaskSpecifications.hasPriority(priority));
        spec = and(spec, TaskSpecifications.hasGoal(goalId));
        spec = and(spec, TaskSpecifications.dueBefore(dueBefore));
        spec = and(spec, TaskSpecifications.matchesText(search));
        if (overdueOnly) {
            spec = and(spec, TaskSpecifications.overdueAsOf(LocalDate.now()));
        }
        Page<Task> result = taskRepository.findAll(spec, pageable(page, size, sort));
        return PageResponse.of(result.map(task -> toResponse(task, goalTitles(userId, result.getContent()))));
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request) {
        Task task = require(userId, taskId);
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("VALIDATION_FAILED", "title must not be blank");
            }
            task.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            task.setDescription(trimToNull(request.description()));
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.goalId() != null) {
            task.setGoalId(resolveGoal(userId, request.goalId()));
        }
        if (request.tags() != null) {
            task.setTags(joinTags(request.tags()));
        }
        if (request.status() != null && request.status() != task.getStatus()) {
            task.setStatus(request.status());
            task.setCompletedAt(request.status() == TaskStatus.COMPLETED ? Instant.now() : null);
        }
        return toResponse(taskRepository.save(task), goalTitles(userId, List.of(task)));
    }

    @Transactional
    public TaskResponse updateStatus(Long userId, Long taskId, TaskStatus status) {
        if (status == null) {
            throw new BadRequestException("VALIDATION_FAILED", "status is required");
        }
        Task task = require(userId, taskId);
        task.setStatus(status);
        task.setCompletedAt(status == TaskStatus.COMPLETED ? Instant.now() : null);
        return toResponse(taskRepository.save(task), goalTitles(userId, List.of(task)));
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        taskRepository.delete(require(userId, taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> overdue(Long userId) {
        List<Task> tasks = taskRepository.findOverdue(userId, LocalDate.now(),
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        return tasks.stream().map(t -> toResponse(t, goalTitles(userId, tasks))).toList();
    }

    @Transactional(readOnly = true)
    public List<Task> entitiesForGoal(Long userId, Long goalId) {
        return taskRepository.findAllByUserIdAndGoalId(userId, goalId);
    }

    /** Every task owned by the user. Used by the dashboard and analytics read models. */
    @Transactional(readOnly = true)
    public List<Task> entitiesForUser(Long userId) {
        return taskRepository.findAllByUserId(userId);
    }

    /** Maps entities to DTOs with goal titles resolved in one batch. */
    @Transactional(readOnly = true)
    public List<TaskResponse> responsesFor(Long userId, List<Task> tasks) {
        Map<Long, String> titles = goalTitles(userId, tasks);
        return tasks.stream().map(task -> toResponse(task, titles)).toList();
    }

    public Task require(Long userId, Long taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private Long resolveGoal(Long userId, Long goalId) {
        if (goalId == null) {
            return null;
        }
        return goalRepository.findById(goalId)
                .filter(goal -> goal.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId))
                .getId();
    }

    private Map<Long, String> goalTitles(Long userId, List<Task> tasks) {
        List<Long> goalIds = tasks.stream().map(Task::getGoalId).filter(java.util.Objects::nonNull).distinct().toList();
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return goalRepository.findAllById(goalIds).stream()
                .filter(goal -> goal.getUserId().equals(userId))
                .collect(java.util.stream.Collectors.toMap(
                        com.aicommandcenter.goal.entity.Goal::getId,
                        com.aicommandcenter.goal.entity.Goal::getTitle));
    }

    public static Specification<Task> and(Specification<Task> base, Specification<Task> extra) {
        return extra == null ? base : base.and(extra);
    }

    private Pageable pageable(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Sort order = parseSort(sort);
        return PageRequest.of(safePage, safeSize, order);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("status"), Sort.Order.asc("dueDate"), Sort.Order.desc("createdAt"));
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            throw new BadRequestException("INVALID_SORT", "Unsupported sort property: " + property);
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, property));
    }

    private TaskResponse toResponse(Task task, Map<Long, String> goalTitles) {
        boolean overdue = task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now())
                && task.getStatus() != null
                && task.getStatus().isOpen();
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getGoalId(),
                task.getGoalId() == null ? null : goalTitles.get(task.getGoalId()),
                splitTags(task.getTags()),
                overdue,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt());
    }

    private static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                cleaned.add(tag.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
