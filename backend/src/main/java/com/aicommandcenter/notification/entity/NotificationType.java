package com.aicommandcenter.notification.entity;

/** Notification categories. Kept small on purpose — one row per derived fact, nothing else. */
public enum NotificationType {
    OVERDUE_TASK,
    DEADLINE_SOON,
    GOAL_DEADLINE,
    GOAL_COMPLETED,
    AI_ACTION,
    SYSTEM
}
