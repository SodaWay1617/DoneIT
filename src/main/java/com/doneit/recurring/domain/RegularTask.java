package com.doneit.recurring.domain;

import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record RegularTask(
        Long id,
        Long userId,
        Long projectId,
        Long taskNumber,
        String taskKey,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        RecurrenceType recurrenceType,
        LocalTime occurrenceTime,
        LocalDate anchorDate,
        RegularStatus regularStatus,
        LocalDateTime activatedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long statusPosition
) {
    public RegularTask {
        if (status == TaskStatus.TODO || status == TaskStatus.PAUSED) {
            throw new IllegalArgumentException("TODO and PAUSED are not available for recurring tasks");
        }
        if (recurrenceType.requiresAnchorDate() != (anchorDate != null)) {
            throw new IllegalArgumentException("Anchor date does not match recurrence type");
        }
        if (regularStatus == RegularStatus.ACTIVE && activatedAt == null) {
            throw new IllegalArgumentException("Active recurring task requires activatedAt");
        }
        if (status.isFinished() && finishedAt == null) {
            throw new IllegalArgumentException("Finished recurring task requires finishedAt");
        }
    }

    public boolean occursOn(LocalDate date) {
        if (activatedAt == null || date.isBefore(activatedAt.toLocalDate())) return false;
        if (finishedAt != null && date.isAfter(finishedAt.toLocalDate())) return false;
        return recurrenceType.occursOn(date, anchorDate);
    }
}
