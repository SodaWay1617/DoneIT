package com.doneit.task.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailyTasksView(
        LocalDate selectedDate,
        List<TaskListItemView> activeTasks,
        List<TaskListItemView> completedTasks
) {

    public DailyTasksView {
        Objects.requireNonNull(selectedDate, "selectedDate is required");
        activeTasks = List.copyOf(Objects.requireNonNull(activeTasks, "activeTasks is required"));
        completedTasks = List.copyOf(Objects.requireNonNull(completedTasks, "completedTasks is required"));
    }
}
