package com.doneit.task.application.command;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.doneit.task.domain.TaskPriority;

public record CreateTaskCommand(
        @NotBlank(message = "Task title is required") String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        Long projectId,
        LocalDate plannedDate,
        boolean backlog,
        TaskPriority priority
) {
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt) {
        this(title,description,plannedForAt,deadlineAt,null,null,false,TaskPriority.NONE);
    }
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,Long projectId){this(title,description,plannedForAt,deadlineAt,projectId,null,false,TaskPriority.NONE);}
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,Long projectId,LocalDate plannedDate,boolean backlog){this(title,description,plannedForAt,deadlineAt,projectId,plannedDate,backlog,TaskPriority.NONE);}

    public CreateTaskCommand {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (priority == null) priority = TaskPriority.NONE;
    }
}
