package com.aicommandcenter.goal.service;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.exception.ConflictException;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class GoalService {

    /** Tasks that should be auto-completed when every task of a goal is done. */
    private static final List<TaskStatus> OPEN_STATUSES = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);

    /** How deep the tree may go: a top-level goal, its sub-goals, and one level beneath those. */
    private static final int MAX_DEPTH_LEVELS = 3;

    /** Bound for the tree walks, so a corrupted chain can never spin forever. */
    private static final int MAX_WALK = 50;

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
        goal.setParentGoalId(resolveParent(userId, request.parentGoalId(), null));
        requireFitsInTree(userId, goal);
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
        if (request.parentGoalId() != null) {
            goal.setParentGoalId(resolveParent(userId, request.parentGoalId(), goal.getId()));
        }
        requireFitsInTree(userId, goal);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void delete(Long userId, Long goalId) {
        Goal goal = require(userId, goalId);

        // Deleting a parent would orphan or silently drop its sub-goals; make the user choose.
        long children = goalRepository.countByUserIdAndParentGoalId(userId, goalId);
        if (children > 0) {
            throw new ConflictException("This goal has " + children + " sub-goal"
                    + (children == 1 ? "" : "s") + "; move or delete them first");
        }

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

    /**
     * Resolves the proposed parent, refusing anything that would corrupt the tree: a goal from
     * another account, the goal itself, or one of its own descendants.
     */
    private Long resolveParent(Long userId, Long parentGoalId, Long movingGoalId) {
        if (parentGoalId == null) {
            return null;
        }

        Goal parent = goalRepository.findById(parentGoalId)
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new BadRequestException("VALIDATION_FAILED", "That parent goal does not exist"));

        if (movingGoalId != null) {
            if (parentGoalId.equals(movingGoalId)) {
                throw new BadRequestException("VALIDATION_FAILED", "A goal cannot be its own parent");
            }
            Long ancestor = parent.getParentGoalId();
            int guard = 0;
            while (ancestor != null && guard++ < MAX_WALK) {
                if (ancestor.equals(movingGoalId)) {
                    throw new BadRequestException("VALIDATION_FAILED",
                            "That would place a goal inside its own sub-goal");
                }
                ancestor = goalRepository.findById(ancestor).map(Goal::getParentGoalId).orElse(null);
            }
        }

        return parent.getId();
    }

    /** Edges above a goal (0 for a top-level goal). */
    private int depthOf(Long userId, Long goalId) {
        int depth = 0;
        Long current = goalId;
        while (current != null && depth < MAX_WALK) {
            Goal goal = goalRepository.findById(current)
                    .filter(candidate -> candidate.getUserId().equals(userId))
                    .orElse(null);
            if (goal == null) {
                break;
            }
            current = goal.getParentGoalId();
            if (current != null) {
                depth++;
            }
        }
        return depth;
    }

    /** Levels below a goal (0 when it has no sub-goals). */
    private int heightOf(Long userId, Long goalId) {
        int height = 0;
        List<Long> frontier = List.of(goalId);
        while (!frontier.isEmpty() && height < MAX_WALK) {
            List<Long> next = new ArrayList<>();
            for (Long id : frontier) {
                goalRepository.findAllByUserIdAndParentGoalIdOrderByCreatedAtAsc(userId, id)
                        .forEach(child -> next.add(child.getId()));
            }
            if (next.isEmpty()) {
                break;
            }
            height++;
            frontier = next;
        }
        return height;
    }

    /**
     * The whole chain has to fit: what sits above the goal, plus the goal, plus everything that
     * already hangs below it. Checking only the new parent would let a deep subtree slip in.
     */
    private void requireFitsInTree(Long userId, Goal goal) {
        int above = goal.getParentGoalId() == null ? 0 : depthOf(userId, goal.getParentGoalId()) + 1;
        int below = goal.getId() == null ? 0 : heightOf(userId, goal.getId());
        if (above + below + 1 > MAX_DEPTH_LEVELS) {
            throw new BadRequestException("VALIDATION_FAILED",
                    "Goals can be nested " + MAX_DEPTH_LEVELS + " levels deep");
        }
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
        long subGoals = goalRepository.countByUserIdAndParentGoalId(goal.getUserId(), goal.getId());
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getDeadline(),
                goal.getPriority(), goal.getStatus(), goal.getNotes(), total, completed, percent, overdue,
                openTitles, goal.getParentGoalId(), subGoals, goal.getCreatedAt(), goal.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
