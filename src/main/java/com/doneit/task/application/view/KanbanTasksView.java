package com.doneit.task.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import com.doneit.task.domain.TaskStatus;

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

    public List<KanbanColumnView> columns() {
        List<TaskListItemView> allTasks = Stream.of(openTasks, doneTasks, closedTasks)
                .flatMap(List::stream)
                .toList();
        return java.util.Arrays.stream(TaskStatus.workflowValues())
                .map(status -> new KanbanColumnView(
                        status,
                        allTasks.stream().filter(task -> task.status() == status).toList()
                ))
                .toList();
    }
}
