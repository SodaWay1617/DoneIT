package com.doneit.task.application.view;

import com.doneit.task.domain.TaskStatus;
import java.util.List;

public record KanbanColumnView(TaskStatus status, List<TaskListItemView> tasks) {
    public KanbanColumnView {
        tasks = List.copyOf(tasks);
    }
}
