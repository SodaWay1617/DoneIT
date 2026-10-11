package com.doneit.task.application.view;

import com.doneit.recurring.domain.RegularTask;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;

public record PlanningLoad(int requiredMinutes, int wantedMinutes, int optionalMinutes) {
    public static PlanningLoad empty() { return new PlanningLoad(0, 0, 0); }

    public int minutes(PlanningType type) {
        return switch (type) {
            case REQUIRED -> requiredMinutes;
            case WANTED -> wantedMinutes;
            case OPTIONAL -> optionalMinutes;
        };
    }

    public int totalMinutes() { return requiredMinutes + wantedMinutes + optionalMinutes; }

    public PlanningLoad add(PlanningType type, Integer estimate) {
        int minutes = estimate == null ? 0 : estimate;
        return switch (type) {
            case REQUIRED -> new PlanningLoad(requiredMinutes + minutes, wantedMinutes, optionalMinutes);
            case WANTED -> new PlanningLoad(requiredMinutes, wantedMinutes + minutes, optionalMinutes);
            case OPTIONAL -> new PlanningLoad(requiredMinutes, wantedMinutes, optionalMinutes + minutes);
        };
    }

    public static boolean active(TaskStatus status) {
        return switch (status) {
            case SPECIFICATION, TODO, IN_PROGRESS, DOCUMENTATION -> true;
            default -> false;
        };
    }

    public static PlanningLoad forDay(LocalDate date, List<TaskListItemView> tasks, List<RegularTask> regularTasks) {
        PlanningLoad load = empty();
        for (TaskListItemView task : tasks) {
            boolean scheduled = date.equals(task.plannedDate())
                    || task.plannedForAt() != null && date.equals(task.plannedForAt().toLocalDate());
            if (active(task.status()) && scheduled) load = load.add(task.planningType(), task.estimateMinutes());
        }
        // The recurring projection already excludes completed and skipped occurrences.
        for (RegularTask task : regularTasks) {
            if (active(task.status()) && task.occursOn(date)) load = load.add(task.planningType(), task.estimateMinutes());
        }
        return load;
    }
}
