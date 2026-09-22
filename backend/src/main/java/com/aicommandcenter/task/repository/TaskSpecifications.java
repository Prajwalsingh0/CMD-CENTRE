package com.aicommandcenter.task.repository;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/** Composable predicates for the task list endpoint. Every predicate is scoped to the owner. */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasStatuses(List<TaskStatus> statuses) {
        return (statuses == null || statuses.isEmpty()) ? null : (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Task> hasPriority(Priority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> hasGoal(Long goalId) {
        return goalId == null ? null : (root, query, cb) -> cb.equal(root.get("goalId"), goalId);
    }

    public static Specification<Task> dueBefore(LocalDate date) {
        return date == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), date);
    }

    public static Specification<Task> overdueAsOf(LocalDate today) {
        return today == null ? null : (root, query, cb) -> cb.and(
                cb.lessThan(root.get("dueDate"), today),
                root.get("status").in(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)));
    }

    public static Specification<Task> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), pattern));
    }
}
