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
        boolean overdue
) {

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
        boolean overdue = task.deadlineAt() != null
                && task.deadlineAt().toLocalDate().isBefore(referenceDate)
                && task.status() == TaskStatus.OPEN;

        return new TaskListItemView(
                task.id(),
                task.title(),
                task.description(),
                task.status(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.isBacklog(),
                overdue
        );
    }
}
