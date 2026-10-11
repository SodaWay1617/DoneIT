package com.doneit.task.application.command;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record RescheduleTaskCommand(
        @NotNull(message = "Task id is required") Long taskId,
        LocalDateTime plannedForAt,
        LocalDate plannedDate
) {

    public RescheduleTaskCommand(Long taskId, LocalDateTime plannedForAt) {
        this(taskId, plannedForAt, null);
    }

    public RescheduleTaskCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if ((plannedForAt == null) == (plannedDate == null)) {
            throw new IllegalArgumentException("Exactly one planned date or datetime is required");
        }
    }
}
