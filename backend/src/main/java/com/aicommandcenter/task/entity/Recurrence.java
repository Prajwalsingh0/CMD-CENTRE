package com.aicommandcenter.task.entity;

import java.time.LocalDate;

/**
 * How often a task repeats. Completing an occurrence queues the next one with the due date
 * advanced by {@link #next(LocalDate)}.
 */
public enum Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    /** Whether this value describes an actual series rather than a one-off task. */
    public boolean repeats() {
        return this != NONE;
    }

    /**
     * The due date of the occurrence after {@code from}.
     *
     * <p>{@code plusMonths} clamps to the end of the month, so a task due on 31 January recurs on
     * 28 or 29 February rather than skipping February entirely - which is what a person means by
     * "monthly".</p>
     */
    public LocalDate next(LocalDate from) {
        if (from == null) {
            return null;
        }
        return switch (this) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case NONE -> null;
        };
    }
}