package com.doneit.task.application.view;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;
import com.doneit.task.domain.TaskPriority;

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
        boolean overdue,
        Long projectId,
        String taskCode,
        LocalDate plannedDate,
        TaskPriority priority,
        Integer estimateMinutes,
        int spentMinutes
) {
    public TaskListItemView(Long id,String title,String description,TaskStatus status,LocalDateTime plannedForAt,
                            LocalDateTime deadlineAt,boolean backlog,boolean overdue,Long projectId,String taskCode,
                            LocalDate plannedDate,TaskPriority priority,Integer estimateMinutes) {
        this(id,title,description,status,plannedForAt,deadlineAt,backlog,overdue,projectId,taskCode,plannedDate,
                priority,estimateMinutes,0);
    }
    public TaskListItemView(Long id,String title,String description,TaskStatus status,LocalDateTime plannedForAt,
                            LocalDateTime deadlineAt,boolean backlog,boolean overdue,Long projectId,String taskCode,
                            LocalDate plannedDate,TaskPriority priority) {
        this(id,title,description,status,plannedForAt,deadlineAt,backlog,overdue,projectId,taskCode,plannedDate,priority,null);
    }
    public TaskListItemView(Long id,String title,String description,TaskStatus status,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean overdue){this(id,title,description,status,planned,deadline,backlog,overdue,null,null,null,TaskPriority.NONE);}
    public TaskListItemView(Long id,String title,String description,TaskStatus status,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean overdue,Long projectId,String taskCode){this(id,title,description,status,planned,deadline,backlog,overdue,projectId,taskCode,null,TaskPriority.NONE);}

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
        return from(task, today, true);
    }

    public static TaskListItemView from(Task task, LocalDate today, boolean showProjectInTitle) {
        LocalDate referenceDate = today == null ? LocalDate.now() : today;
        boolean plannedBeforeReferenceDate = task.plannedForAt() != null
                && task.plannedForAt().toLocalDate().isBefore(referenceDate);
        boolean plannedDateBeforeReferenceDate = task.plannedDate() != null && task.plannedDate().isBefore(referenceDate);
        boolean deadlineBeforeReferenceDate = task.deadlineAt() != null
                && task.deadlineAt().toLocalDate().isBefore(referenceDate);
        boolean overdue = task.status().isActiveFlow()
                && (plannedBeforeReferenceDate || plannedDateBeforeReferenceDate || deadlineBeforeReferenceDate);

        return new TaskListItemView(
                task.id(),
                displayTitle(task, showProjectInTitle),
                task.description(),
                task.status(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.isBacklog(),
                overdue,
                task.projectId(),
                displayTaskCode(task),
                task.plannedDate(),
                task.priority(),
                task.estimateMinutes(),
                task.spentMinutes()
        );
    }

    private static String displayTitle(Task task, boolean showProjectInTitle) {
        String taskCode = displayTaskCode(task);
        return showProjectInTitle && taskCode != null ? taskCode + "  " + task.title() : task.title();
    }

    private static String displayTaskCode(Task task) {
        if (task.taskKey() == null) return null;
        String[] keyParts = task.taskKey().split("___", 2);
        String code = keyParts[0];
        if (keyParts.length == 2 && code.startsWith("MAIN-" + keyParts[1] + "-")) {
            return "MAIN-" + code.substring(("MAIN-" + keyParts[1] + "-").length());
        }
        return code;
    }
}
