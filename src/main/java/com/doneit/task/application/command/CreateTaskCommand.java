package com.doneit.task.application.command;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;

public record CreateTaskCommand(
        @NotBlank(message = "Task title is required") String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        Long projectId,
        LocalDate plannedDate,
        boolean backlog,
        TaskPriority priority,
        TaskStatus status,
        Integer estimateMinutes
) {
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,
                             Long projectId,LocalDate plannedDate,boolean backlog,TaskPriority priority,TaskStatus status) {
        this(title,description,plannedForAt,deadlineAt,projectId,plannedDate,backlog,priority,status,null);
    }
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt) {
        this(title,description,plannedForAt,deadlineAt,null,null,false,TaskPriority.NONE,TaskStatus.IN_PROGRESS);
    }
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,Long projectId){this(title,description,plannedForAt,deadlineAt,projectId,null,false,TaskPriority.NONE,TaskStatus.IN_PROGRESS);}
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,Long projectId,LocalDate plannedDate,boolean backlog){this(title,description,plannedForAt,deadlineAt,projectId,plannedDate,backlog,TaskPriority.NONE,backlog?TaskStatus.BACKLOG:TaskStatus.IN_PROGRESS);}
    public CreateTaskCommand(String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,Long projectId,LocalDate plannedDate,boolean backlog,TaskPriority priority){this(title,description,plannedForAt,deadlineAt,projectId,plannedDate,backlog,priority,backlog?TaskStatus.BACKLOG:TaskStatus.IN_PROGRESS);}

    public CreateTaskCommand {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (priority == null) priority = TaskPriority.NONE;
        if (status == null) status = backlog ? TaskStatus.BACKLOG : TaskStatus.NEW;
        if (estimateMinutes != null && estimateMinutes <= 0) throw new IllegalArgumentException("Estimate must be positive");
    }
}
