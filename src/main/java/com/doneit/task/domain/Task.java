package com.doneit.task.domain;

import java.time.LocalDateTime;

public record Task(
        Long id,
        Long userId,
        String title,
        String description,
        TaskStatus status,
        LocalDateTime plannedForAt,
        LocalDateTime deadlineAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime closedAt
) {

    public boolean isBacklog() {
        return plannedForAt == null;
    }
}
