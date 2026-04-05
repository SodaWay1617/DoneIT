package com.doneit.task.application.command;

import java.time.LocalDateTime;

public record EditTaskCommand(
        Long taskId,
        String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt
) {

    public EditTaskCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
    }
}
