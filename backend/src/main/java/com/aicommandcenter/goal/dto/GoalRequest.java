package com.aicommandcenter.goal.dto;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.goal.entity.GoalStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GoalRequest(
        @Size(min = 1, max = 180) String title,
        @Size(max = 4000) String description,
        LocalDate deadline,
        Priority priority,
        GoalStatus status,
        @Size(max = 8000) String notes) {
}
