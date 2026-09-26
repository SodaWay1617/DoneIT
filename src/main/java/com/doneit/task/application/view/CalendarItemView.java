package com.doneit.task.application.view;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CalendarItemView(
        Long taskId,
        CalendarOccurrenceType occurrenceType,
        LocalDate date,
        LocalDateTime dateTime,
        String title,
        TaskStatus taskStatus,
        boolean deadlineHighlighted
) {

    public static CalendarItemView planned(Task task) {
        return new CalendarItemView(
                task.id(),
                CalendarOccurrenceType.PLANNED,
                task.plannedForAt().toLocalDate(),
                task.plannedForAt(),
                task.title(),
                task.status(),
                task.deadlineAt() != null && task.deadlineAt().toLocalDate().equals(task.plannedForAt().toLocalDate())
        );
    }

    public static CalendarItemView deadline(Task task) {
        return new CalendarItemView(
                task.id(),
                CalendarOccurrenceType.DEADLINE,
                task.deadlineAt().toLocalDate(),
                task.deadlineAt(),
                task.title(),
                task.status(),
                true
        );
    }
}
