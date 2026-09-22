package com.aicommandcenter.task.dto;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.task.entity.TaskStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Create/update payload. {@code status} may be null on create (defaults to TODO);
 * on update the service merges only the non-null fields it receives.
 */
public record TaskRequest(
        @Size(min = 1, max = 180) String title,
        @Size(max = 4000) String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        Long goalId,
        List<@Size(max = 40) String> tags) {
}
