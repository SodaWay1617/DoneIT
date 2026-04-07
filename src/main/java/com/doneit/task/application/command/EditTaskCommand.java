package com.doneit.task.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EditTaskCommand(
        @NotNull(message = "Task id is required") Long taskId,
        @NotBlank(message = "Task title is required") String title,
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