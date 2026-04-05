package com.doneit.task.application.view;

import java.util.List;
import java.util.Objects;

public record BacklogTasksView(List<TaskListItemView> tasks) {

    public BacklogTasksView {
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks is required"));
    }
}
