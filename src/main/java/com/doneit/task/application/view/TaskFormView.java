package com.doneit.task.application.view;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;

public record TaskFormView(
        Long taskId,
        String title,
        String description,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        boolean backlog,
        boolean editMode,
        Long projectId,
        LocalDate plannedDate,
        TaskPriority priority,
        TaskStatus status,
        Integer estimateMinutes,
        int spentMinutes
) {
    public TaskFormView(Long taskId,String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,
                        boolean backlog,boolean editMode,Long projectId,LocalDate plannedDate,
                        TaskPriority priority,TaskStatus status,Integer estimateMinutes) {
        this(taskId,title,description,plannedForAt,deadlineAt,backlog,editMode,projectId,plannedDate,priority,status,estimateMinutes,0);
    }
    public TaskFormView(Long taskId,String title,String description,LocalDateTime plannedForAt,LocalDateTime deadlineAt,
                        boolean backlog,boolean editMode,Long projectId,LocalDate plannedDate,
                        TaskPriority priority,TaskStatus status) {
        this(taskId,title,description,plannedForAt,deadlineAt,backlog,editMode,projectId,plannedDate,priority,status,null);
    }
    public TaskFormView(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean edit){this(id,title,description,planned,deadline,backlog,edit,null,null,TaskPriority.NONE,backlog?TaskStatus.BACKLOG:TaskStatus.IN_PROGRESS);}
    public TaskFormView(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean edit,Long projectId){this(id,title,description,planned,deadline,backlog,edit,projectId,null,TaskPriority.NONE,backlog?TaskStatus.BACKLOG:TaskStatus.IN_PROGRESS);}
    public TaskFormView(Long id,String title,String description,LocalDateTime planned,LocalDateTime deadline,boolean backlog,boolean edit,Long projectId,LocalDate plannedDate){this(id,title,description,planned,deadline,backlog,edit,projectId,plannedDate,TaskPriority.NONE,backlog?TaskStatus.BACKLOG:TaskStatus.IN_PROGRESS);}

    public static TaskFormView forCreate(LocalDateTime plannedForAt) {
        return new TaskFormView(null, "", "", plannedForAt, null, false, false, null, null, TaskPriority.NONE, TaskStatus.NEW, null, 0);
    }
}
