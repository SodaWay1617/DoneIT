package com.doneit.recurring.application;

import com.doneit.recurring.domain.RecurrenceType;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public class RegularTaskForm {
    private Long id;
    @NotBlank private String title;
    private String description;
    @NotNull private Long projectId;
    @NotNull private TaskStatus status = TaskStatus.NEW;
    @NotNull private TaskPriority priority = TaskPriority.NONE;
    @NotNull private RecurrenceType recurrenceType = RecurrenceType.DAILY;
    @Min(value = 1, message = "Repeat interval must be at least one")
    private int repeatInterval = 1;
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime occurrenceTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate anchorDate;
    @Min(value = 1, message = "Estimate must be at least one minute")
    private Integer estimateMinutes;
    @Min(value = 0, message = "Spent time cannot be negative")
    private int spentMinutes;

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
    public int getRepeatInterval() { return repeatInterval; }
    public void setRepeatInterval(int repeatInterval) { this.repeatInterval = repeatInterval; }
    public LocalTime getOccurrenceTime() { return occurrenceTime; }
    public void setOccurrenceTime(LocalTime occurrenceTime) { this.occurrenceTime = occurrenceTime; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
    public Integer getEstimateMinutes() { return estimateMinutes; }
    public void setEstimateMinutes(Integer estimateMinutes) { this.estimateMinutes = estimateMinutes; }
    public int getSpentMinutes() { return spentMinutes; }
    public void setSpentMinutes(int spentMinutes) { this.spentMinutes = spentMinutes; }
}
