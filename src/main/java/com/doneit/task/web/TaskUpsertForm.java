package com.doneit.task.web;

import com.doneit.task.application.view.TaskFormView;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class TaskUpsertForm {

    private Long taskId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime plannedForAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime deadlineAt;

    private boolean editMode;

    public static TaskUpsertForm from(TaskFormView taskFormView) {
        TaskUpsertForm form = new TaskUpsertForm();
        form.setTaskId(taskFormView.taskId());
        form.setTitle(taskFormView.title());
        form.setDescription(taskFormView.description());
        form.setPlannedForAt(taskFormView.plannedForAt());
        form.setDeadlineAt(taskFormView.deadlineAt());
        form.setEditMode(taskFormView.editMode());
        return form;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

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
}