package com.doneit.task.web;

import com.doneit.task.domain.PlanningType;

import com.doneit.task.application.view.TaskFormView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.LocalDate;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;

public class TaskUpsertForm {
    private java.util.List<String> tagNames = new java.util.ArrayList<>();
    private boolean tagsPresent;
    public java.util.List<String> getTagNames() { return tagNames; }
    public void setTagNames(java.util.List<String> names) { tagNames = names == null ? new java.util.ArrayList<>() : names; }
    public boolean isTagsPresent() { return tagsPresent; }
    public void setTagsPresent(boolean value) { tagsPresent = value; }
    @jakarta.validation.constraints.AssertTrue(message = "Invalid tags")
    public boolean isValidTags() { return com.doneit.tag.application.TagNames.valid(tagNames); }

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
    private boolean withoutPlannedDate;
    private boolean withoutDeadline;
    private boolean plannedWithTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedDate;
    private TaskPriority priority = TaskPriority.NONE;
    private PlanningType planningType = PlanningType.REQUIRED;
    public PlanningType getPlanningType() { return planningType; }
    public void setPlanningType(PlanningType planningType) { this.planningType = planningType == null ? PlanningType.REQUIRED : planningType; }
    private TaskStatus status = TaskStatus.NEW;
    @Min(value = 1, message = "Estimate must be at least one minute")
    private Integer estimateMinutes;
    private boolean explicitEndTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private java.time.LocalTime endTime;
    public boolean isExplicitEndTime() { return explicitEndTime; }
    public void setExplicitEndTime(boolean value) { explicitEndTime = value; }
    public java.time.LocalTime getEndTime() { return endTime; }
    public void setEndTime(java.time.LocalTime value) { endTime = value; }
    @jakarta.validation.constraints.AssertTrue(message = "Choose a start time and a different end time")
    public boolean isValidEndTime() {
        if (!explicitEndTime) return true;
        if (withoutPlannedDate || !plannedWithTime || status == TaskStatus.NEW || status == TaskStatus.BACKLOG || status == TaskStatus.PAUSED) return true;
        return plannedForAt != null && endTime != null && !plannedForAt.toLocalTime().equals(endTime);
    }
    @Min(value = 0, message = "Spent time cannot be negative")
    private int spentMinutes;

    public static TaskUpsertForm from(TaskFormView taskFormView) {
        TaskUpsertForm form = new TaskUpsertForm();
        form.setTaskId(taskFormView.taskId());
        form.setProjectId(taskFormView.projectId());
        form.setTitle(taskFormView.title());
        form.setDescription(taskFormView.description());
        boolean plannedWithTime = taskFormView.editMode() && taskFormView.plannedForAt() != null;
        form.setPlannedWithTime(plannedWithTime);
        form.setPlannedForAt(plannedWithTime ? taskFormView.plannedForAt() : null);
        form.setDeadlineAt(taskFormView.deadlineAt());
        form.setEditMode(taskFormView.editMode());
        form.setBacklog(taskFormView.backlog());
        form.setWithoutPlannedDate(taskFormView.editMode()
                && taskFormView.plannedForAt() == null
                && taskFormView.plannedDate() == null);
        form.setWithoutDeadline(taskFormView.editMode() && taskFormView.deadlineAt() == null);
        form.setPlannedDate(taskFormView.plannedDate() != null
                ? taskFormView.plannedDate()
                : taskFormView.plannedForAt() == null ? null : taskFormView.plannedForAt().toLocalDate());
        form.setPriority(taskFormView.priority());
        form.setPlanningType(taskFormView.planningType());
        form.setStatus(taskFormView.status());
        form.setEstimateMinutes(taskFormView.estimateMinutes());
        form.setSpentMinutes(taskFormView.spentMinutes());
        form.setEndTime(taskFormView.endTime());
        form.setExplicitEndTime(taskFormView.endTime() != null);
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
    public boolean isWithoutPlannedDate() { return withoutPlannedDate; }
    public void setWithoutPlannedDate(boolean withoutPlannedDate) { this.withoutPlannedDate = withoutPlannedDate; }
    public boolean isWithoutDeadline() { return withoutDeadline; }
    public void setWithoutDeadline(boolean withoutDeadline) { this.withoutDeadline = withoutDeadline; }
    public boolean isPlannedWithTime() { return plannedWithTime; }
    public void setPlannedWithTime(boolean plannedWithTime) { this.plannedWithTime = plannedWithTime; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority == null ? TaskPriority.NONE : priority; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status == null ? TaskStatus.NEW : status; }
    public Integer getEstimateMinutes() { return estimateMinutes; }
    public void setEstimateMinutes(Integer estimateMinutes) { this.estimateMinutes = estimateMinutes; }
    public int getSpentMinutes() { return spentMinutes; }
    public void setSpentMinutes(int spentMinutes) { this.spentMinutes = spentMinutes; }
}
