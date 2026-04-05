package com.doneit.task.application.command;

import java.time.LocalDateTime;

public record CreateTaskCommand(
        String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt
) {

    public CreateTaskCommand {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
    }
}
