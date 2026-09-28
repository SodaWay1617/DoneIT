package com.doneit.task.application.view;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;
import com.doneit.task.domain.TaskPriority;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CalendarItemView(
        Long taskId,
        CalendarOccurrenceType occurrenceType,
        LocalDate date,
        LocalDateTime dateTime,
        String title,
        TaskStatus taskStatus,
        boolean deadlineHighlighted,
        TaskPriority priority,
        boolean exactTime
) {
    public CalendarItemView(Long taskId, CalendarOccurrenceType occurrenceType, LocalDate date,
                            LocalDateTime dateTime, String title, TaskStatus taskStatus,
                            boolean deadlineHighlighted) {
        this(taskId, occurrenceType, date, dateTime, title, taskStatus, deadlineHighlighted,
                TaskPriority.NONE, true);
    }

    public static CalendarItemView planned(Task task) {
        return planned(task, true);
    }

    public static CalendarItemView planned(Task task, boolean showProjectInTitle) {
        return new CalendarItemView(
                task.id(),
                CalendarOccurrenceType.PLANNED,
                task.plannedForAt().toLocalDate(),
                task.plannedForAt(),
                displayTitle(task, showProjectInTitle),
                task.status(),
                task.deadlineAt() != null && task.deadlineAt().toLocalDate().equals(task.plannedForAt().toLocalDate()),
                task.priority(),
                true
        );
    }

    public static CalendarItemView plannedDate(Task task) {
        return plannedDate(task, true);
    }

    public static CalendarItemView plannedDate(Task task, boolean showProjectInTitle) {
        return new CalendarItemView(task.id(), CalendarOccurrenceType.PLANNED, task.plannedDate(),
                task.plannedDate().atStartOfDay(), displayTitle(task, showProjectInTitle), task.status(),
                task.deadlineAt() != null && task.deadlineAt().toLocalDate().equals(task.plannedDate()),
                task.priority(), false);
    }

    public static CalendarItemView deadline(Task task) {
        return deadline(task, true);
    }

    public static CalendarItemView deadline(Task task, boolean showProjectInTitle) {
        return new CalendarItemView(
                task.id(),
                CalendarOccurrenceType.DEADLINE,
                task.deadlineAt().toLocalDate(),
                task.deadlineAt(),
                displayTitle(task, showProjectInTitle),
                task.status(),
                true,
                task.priority(),
                true
        );
    }

    private static String displayTitle(Task task, boolean showProjectInTitle) {
        if (!showProjectInTitle || task.taskKey() == null) return task.title();
        String[] keyParts = task.taskKey().split("___", 2);
        String code = keyParts[0];
        if (keyParts.length == 2 && code.startsWith("MAIN-" + keyParts[1] + "-")) {
            code = "MAIN-" + code.substring(("MAIN-" + keyParts[1] + "-").length());
        }
        return code + "  " + task.title();
    }
}
