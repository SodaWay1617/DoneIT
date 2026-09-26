package com.doneit.task.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record KanbanTasksView(
        LocalDate selectedDate,
        List<TaskListItemView> openTasks,
        List<TaskListItemView> doneTasks,
        List<TaskListItemView> closedTasks
) {

    public KanbanTasksView {
        Objects.requireNonNull(selectedDate, "selectedDate is required");
        openTasks = List.copyOf(Objects.requireNonNull(openTasks, "openTasks is required"));
        doneTasks = List.copyOf(Objects.requireNonNull(doneTasks, "doneTasks is required"));
        closedTasks = List.copyOf(Objects.requireNonNull(closedTasks, "closedTasks is required"));
    }
}
