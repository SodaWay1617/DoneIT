package com.doneit.task.application.command;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RescheduleTaskCommand(
        @NotNull(message = "Task id is required") Long taskId,
        @NotNull(message = "plannedForAt is required") LocalDateTime plannedForAt
) {

    public RescheduleTaskCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (plannedForAt == null) {
            throw new IllegalArgumentException("plannedForAt is required");
        }
    }
}