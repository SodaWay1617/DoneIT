package com.doneit.task.application.command;

import com.doneit.task.domain.PlanningType;

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
        Integer estimateMinutes,
        PlanningType planningType,
        java.time.LocalTime endTime
) {
    public CreateTaskCommand(
        @NotBlank(message = "Task title is required") String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        Long projectId,
        LocalDate plannedDate,
        boolean backlog,
        TaskPriority priority,
        TaskStatus status,
        Integer estimateMinutes,
        PlanningType planningType
    ) {
        this(title, description, plannedForAt, deadlineAt, projectId, plannedDate, backlog, priority, status, estimateMinutes, planningType, null);
    }

    public CreateTaskCommand(
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
        this(title, description, plannedForAt, deadlineAt, projectId, plannedDate, backlog, priority, status, estimateMinutes, PlanningType.REQUIRED);
    }

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
        com.doneit.task.domain.TaskInterval.validate(plannedForAt == null ? null : plannedForAt.toLocalTime(), endTime);
        if (endTime != null) estimateMinutes = com.doneit.task.domain.TaskInterval.minutes(plannedForAt.toLocalTime(),endTime);
        if (planningType == null) planningType = PlanningType.REQUIRED;
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (priority == null) priority = TaskPriority.NONE;
        if (status == null) status = backlog ? TaskStatus.BACKLOG : TaskStatus.NEW;
        if (estimateMinutes != null && estimateMinutes <= 0) throw new IllegalArgumentException("Estimate must be positive");
    }
}
