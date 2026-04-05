package com.doneit.task.application.command;

import java.time.LocalDateTime;

public record RescheduleTaskCommand(
        Long taskId,
        LocalDateTime plannedForAt
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
