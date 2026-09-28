package com.doneit.task.application.view;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record TaskFormView(
        Long taskId,
        String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        boolean backlog,
        boolean editMode,
        Long projectId,
        LocalDate plannedDate
) {
    public TaskFormView(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean edit){this(id,title,description,planned,deadline,backlog,edit,null,null);}
    public TaskFormView(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean edit,Long projectId){this(id,title,description,planned,deadline,backlog,edit,projectId,null);}

    public static TaskFormView forCreate(LocalDateTime plannedForAt) {
        return new TaskFormView(null, "", "", plannedForAt, null, false, false);
    }
}
