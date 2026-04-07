package com.doneit.task.application.command;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CreateTaskCommand(
        @NotBlank(message = "Task title is required") String title,
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