package com.doneit.recurring.application;

import com.doneit.recurring.domain.RecurrenceType;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class RegularTaskForm {
    private Long id;
    @NotBlank private String title;
    private String description;
    @NotNull private Long projectId;
    @NotNull private TaskStatus status = TaskStatus.NEW;
    @NotNull private TaskPriority priority = TaskPriority.NONE;
    @NotNull private RecurrenceType recurrenceType = RecurrenceType.DAILY;
    private LocalTime occurrenceTime;
    private LocalDate anchorDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public RecurrenceType getRecurrenceType() { return recurrenceType; }
    public void setRecurrenceType(RecurrenceType recurrenceType) { this.recurrenceType = recurrenceType; }
    public LocalTime getOccurrenceTime() { return occurrenceTime; }
    public void setOccurrenceTime(LocalTime occurrenceTime) { this.occurrenceTime = occurrenceTime; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
}
