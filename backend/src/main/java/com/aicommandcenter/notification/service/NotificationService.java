package com.aicommandcenter.notification.service;

import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.goal.entity.Goal;
import com.aicommandcenter.goal.entity.GoalStatus;
import com.aicommandcenter.goal.repository.GoalRepository;
import com.aicommandcenter.notification.dto.NotificationResponse;
import com.aicommandcenter.notification.entity.Notification;
import com.aicommandcenter.notification.entity.NotificationType;
import com.aicommandcenter.notification.repository.NotificationRepository;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Derives notifications from real rows instead of a scheduler pushing invented events.
 *
 * <p>Every notification is keyed by a deterministic dedupe key such as
 * {@code OVERDUE_TASK:91}, so refreshing repeatedly is idempotent and a task that stops being
 * overdue simply stops being recreated. This is the simplest design that is still correct; the
 * brief explicitly asks not to build a notification microservice for this.</p>
 */
@Service
public class NotificationService {

    private static final int DEADLINE_SOON_DAYS = 3;
    private static final int GOAL_DEADLINE_DAYS = 7;

    private final NotificationRepository repository;
    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;

    public NotificationService(NotificationRepository repository,
                               TaskRepository taskRepository,
                               GoalRepository goalRepository) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
    }

    /** Creates any notification that does not exist yet and returns the full current list. */
    @Transactional
    public List<NotificationResponse> refresh(Long userId) {
        LocalDate today = LocalDate.now();

        for (Task task : taskRepository.findOverdue(userId, today, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS))) {
            long daysLate = ChronoUnit.DAYS.between(task.getDueDate(), today);
            create(userId, NotificationType.OVERDUE_TASK, "OVERDUE_TASK:" + task.getId(),
                    "Overdue: " + task.getTitle(),
                    "This task was due " + daysLate + (daysLate == 1 ? " day" : " days") + " ago.");
        }

        for (Task task : taskRepository.findAllByUserId(userId)) {
            if (task.getDueDate() == null || !task.getStatus().isOpen()) {
                continue;
            }
            long daysUntil = ChronoUnit.DAYS.between(today, task.getDueDate());
            if (daysUntil >= 0 && daysUntil <= DEADLINE_SOON_DAYS) {
                create(userId, NotificationType.DEADLINE_SOON, "DEADLINE_SOON:" + task.getId(),
                        "Due soon: " + task.getTitle(),
                        daysUntil == 0 ? "Due today." : "Due in " + daysUntil + (daysUntil == 1 ? " day." : " days."));
            }
        }

        for (Goal goal : goalRepository.findAllByUserIdOrderByCreatedAtDesc(userId)) {
            if (goal.getDeadline() == null) {
                continue;
            }
            long daysUntil = ChronoUnit.DAYS.between(today, goal.getDeadline());
            if (goal.getStatus() == GoalStatus.ACTIVE && daysUntil >= 0 && daysUntil <= GOAL_DEADLINE_DAYS) {
                create(userId, NotificationType.GOAL_DEADLINE, "GOAL_DEADLINE:" + goal.getId(),
                        "Goal deadline approaching: " + goal.getTitle(),
                        daysUntil == 0 ? "Target date is today." : "Target date is in " + daysUntil + " days.");
            }
            if (goal.getStatus() == GoalStatus.COMPLETED) {
                create(userId, NotificationType.GOAL_COMPLETED, "GOAL_COMPLETED:" + goal.getId(),
                        "Goal completed: " + goal.getTitle(),
                        "Every task attached to this goal is done.");
            }
        }

        return list(userId, false);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? repository.findAllByUserIdAndReadOrderByCreatedAtDesc(userId, false)
                : repository.findAllByUserIdOrderByCreatedAtDesc(userId);
        return notifications.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> unreadPreview(Long userId, int limit) {
        return repository.findTop10ByUserIdAndReadOrderByCreatedAtDesc(userId, false).stream()
                .limit(Math.max(1, limit))
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndRead(userId, false);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.setRead(true);
        return toResponse(repository.save(notification));
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = repository.findAllByUserIdAndReadOrderByCreatedAtDesc(userId, false);
        unread.forEach(notification -> notification.setRead(true));
        repository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        repository.delete(notification);
    }

    private void create(Long userId, NotificationType type, String dedupeKey, String title, String message) {
        if (repository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setEventType(type);
        notification.setDedupeKey(dedupeKey);
        notification.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        notification.setMessage(message);
        repository.save(notification);
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getEventType().name(),
                notification.getTitle(), notification.getMessage(), notification.isRead(),
                notification.getCreatedAt());
    }
}
