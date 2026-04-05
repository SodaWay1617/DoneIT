package com.doneit.task.application.command;

public record MoveTaskToBacklogCommand(Long taskId) {

    public MoveTaskToBacklogCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
    }
}
