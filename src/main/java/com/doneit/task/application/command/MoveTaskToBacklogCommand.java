package com.doneit.task.application.command;

import jakarta.validation.constraints.NotNull;

public record MoveTaskToBacklogCommand(@NotNull(message = "Task id is required") Long taskId) {

    public MoveTaskToBacklogCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
    }
}