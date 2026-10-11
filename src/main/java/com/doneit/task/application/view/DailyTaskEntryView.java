package com.doneit.task.application.view;

import com.doneit.recurring.domain.RegularTask;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public record DailyTaskEntryView(TaskListItemView task, RegularTask regularTask) {
    private static final Comparator<DailyTaskEntryView> ORDER = Comparator
            .comparing(DailyTaskEntryView::priority, Comparator.reverseOrder())
            .thenComparing(DailyTaskEntryView::time, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(entry -> switch (entry.status()) {
                case IN_PROGRESS -> 0;
                case TODO -> 1;
                case DOCUMENTATION -> 2;
                case SPECIFICATION -> 3;
                default -> 4;
            })
            .thenComparing(DailyTaskEntryView::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.regularTask != null)
            .thenComparing(DailyTaskEntryView::id);

    public static List<DailyTaskEntryView> sorted(List<TaskListItemView> tasks, List<RegularTask> regularTasks) {
        return Stream.concat(
                tasks.stream().map(task -> new DailyTaskEntryView(task, null)),
                regularTasks.stream().map(task -> new DailyTaskEntryView(null, task)))
                .sorted(ORDER).toList();
    }

    private TaskPriority priority() { return task != null ? task.priority() : regularTask.priority(); }
    private LocalTime time() {
        return task != null
                ? (task.plannedForAt() == null ? null : task.plannedForAt().toLocalTime())
                : regularTask.occurrenceTime();
    }
    private TaskStatus status() { return task != null ? task.status() : regularTask.status(); }
    private String title() { return task != null ? task.title() : regularTask.title(); }
    private Long id() { return task != null ? task.id() : regularTask.id(); }
}
