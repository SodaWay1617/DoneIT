package com.doneit.task.application.command;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record CreateTaskCommand(
        @NotBlank(message = "Task title is required") String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        Long projectId,
        LocalDate plannedDate,
        boolean backlog
) {
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt) {
        this(title,description,plannedForAt,deadlineAt,null,null,false);
    }
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,Long projectId){this(title,description,plannedForAt,deadlineAt,projectId,null,false);}

    public CreateTaskCommand {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
    }
}
