package com.doneit.task.application.view;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskListItemView(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        boolean backlog,
        boolean overdue,
        Long projectId,
        String taskCode
) {
    public TaskListItemView(Long id,String title,String description,TaskStatus status,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean overdue){this(id,title,description,status,planned,deadline,backlog,overdue,null,null);}

    public TaskListItemView {
        if (id == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("Task status is required");
        }
    }

    public static TaskListItemView from(Task task, LocalDate today) {
        LocalDate referenceDate = today == null ? LocalDate.now() : today;
        boolean plannedBeforeReferenceDate = task.plannedForAt() != null
                && task.plannedForAt().toLocalDate().isBefore(referenceDate);
        boolean deadlineBeforeReferenceDate = task.deadlineAt() != null
                && task.deadlineAt().toLocalDate().isBefore(referenceDate);
        boolean overdue = task.status() == TaskStatus.OPEN
                && (plannedBeforeReferenceDate || deadlineBeforeReferenceDate);

        return new TaskListItemView(
                task.id(),
                displayTitle(task),
                task.description(),
                task.status(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.isBacklog(),
                overdue,
                task.projectId(),
                task.taskKey() == null ? null : task.taskKey().split(\u0022___\u0022, 2)[0]
        );
    }

    private static String displayTitle(Task task) {
        if (task.taskKey() == null) return task.title();
        return task.taskKey().split(\u0022___\u0022, 2)[0] + \u0022  \u0022 + task.title();
    }
}
