package com.doneit.task.application.view;

import java.time.LocalDateTime;

public record TaskFormView(
        Long taskId,
        String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        boolean backlog,
        boolean editMode,
        Long projectId
) {
    public TaskFormView(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean edit){this(id,title,description,planned,deadline,backlog,edit,null);}

    public static TaskFormView forCreate(LocalDateTime plannedForAt) {
        return new TaskFormView(null, "", "", plannedForAt, null, false, false);
    }
}
