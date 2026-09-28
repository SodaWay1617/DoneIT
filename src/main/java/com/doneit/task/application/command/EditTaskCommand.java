package com.doneit.task.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;

public record EditTaskCommand(
        @NotNull(message = "Task id is required") Long taskId,
        @NotBlank(message = "Task title is required") String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        Long projectId,
        LocalDate plannedDate,
        boolean backlog,
        TaskPriority priority,
        TaskStatus status
) {
    public EditTaskCommand(Long taskId,String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt) {
        this(taskId,title,description,plannedForAt,deadlineAt,null,null,false,TaskPriority.NONE,null);
    }
    public EditTaskCommand(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,Long projectId){this(id,title,description,planned,deadline,projectId,null,false,TaskPriority.NONE,null);}
    public EditTaskCommand(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,Long projectId,LocalDate plannedDate,boolean backlog){this(id,title,description,planned,deadline,projectId,plannedDate,backlog,TaskPriority.NONE,backlog?TaskStatus.BACKLOG:null);}
    public EditTaskCommand(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,Long projectId,LocalDate plannedDate,boolean backlog,TaskPriority priority){this(id,title,description,planned,deadline,projectId,plannedDate,backlog,priority,backlog?TaskStatus.BACKLOG:null);}

    public EditTaskCommand {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (priority == null) priority = TaskPriority.NONE;
    }
}
