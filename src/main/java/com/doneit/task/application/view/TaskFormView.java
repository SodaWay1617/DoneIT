package com.doneit.task.application.view;

import java.time.LocalDateTime;

public record TaskFormView(
        Long taskId,
        String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        boolean backlog,
        boolean editMode
) {

    public static TaskFormView forCreate(LocalDateTime plannedForAt) {
        return new TaskFormView(null, "", "", plannedForAt, null, false, false);
    }
}