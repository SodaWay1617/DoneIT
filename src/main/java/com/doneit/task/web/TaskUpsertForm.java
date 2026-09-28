package com.doneit.task.web;

import com.doneit.task.application.view.TaskFormView;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;

public class TaskUpsertForm {

    private Long taskId;
    private Long projectId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime plannedForAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime deadlineAt;

    private boolean editMode;
    private boolean backlog;
    private boolean permanent;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedDate;
    private TaskPriority priority = TaskPriority.NONE;
    private TaskStatus status = TaskStatus.NEW;

    public static TaskUpsertForm from(TaskFormView taskFormView) {
        TaskUpsertForm form = new TaskUpsertForm();
        form.setTaskId(taskFormView.taskId());
        form.setProjectId(taskFormView.projectId());
        form.setTitle(taskFormView.title());
        form.setDescription(taskFormView.description());
        form.setPlannedForAt(taskFormView.plannedForAt());
        form.setDeadlineAt(taskFormView.deadlineAt());
        form.setEditMode(taskFormView.editMode());
        form.setBacklog(taskFormView.backlog());
        form.setPermanent(!taskFormView.backlog()
                && taskFormView.plannedForAt() == null
                && taskFormView.plannedDate() == null);
        form.setPlannedDate(taskFormView.plannedDate());
        form.setPriority(taskFormView.priority());
        form.setStatus(taskFormView.status());
        return form;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getPlannedForAt() {
        return plannedForAt;
    }

    public void setPlannedForAt(LocalDateTime plannedForAt) {
        this.plannedForAt = plannedForAt;
    }

    public LocalDateTime getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(LocalDateTime deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public boolean isBacklog() { return backlog; }
    public void setBacklog(boolean backlog) { this.backlog = backlog; }
    public boolean isPermanent() { return permanent; }
    public void setPermanent(boolean permanent) { this.permanent = permanent; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority == null ? TaskPriority.NONE : priority; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status == null ? TaskStatus.NEW : status; }
}
