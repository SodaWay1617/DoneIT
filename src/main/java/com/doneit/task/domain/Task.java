package com.doneit.task.domain;

import java.time.LocalDateTime;
import java.util.Objects;

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

    public Task {
        if (userId == null) {
            throw new IllegalArgumentException("Task userId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("Task status is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Task createdAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Task updatedAt is required");
        }
        if (status == TaskStatus.OPEN && (completedAt != null || closedAt != null)) {
            throw new IllegalArgumentException("Open task cannot have completion timestamps");
        }
        if (status == TaskStatus.DONE && completedAt == null) {
            throw new IllegalArgumentException("Done task must have completedAt timestamp");
        }
        if (status == TaskStatus.DONE && closedAt != null) {
            throw new IllegalArgumentException("Done task cannot have closedAt timestamp");
        }
        if (status == TaskStatus.CLOSED && closedAt == null) {
            throw new IllegalArgumentException("Closed task must have closedAt timestamp");
        }
        if (status == TaskStatus.CLOSED && completedAt != null) {
            throw new IllegalArgumentException("Closed task cannot have completedAt timestamp");
        }
    }

    public boolean isBacklog() {
        return plannedForAt == null;
    }

    public boolean isActive() {
        return status == TaskStatus.OPEN;
    }

    public boolean isVisibleInActiveDatedList() {
        return isActive() && !isBacklog();
    }

    public boolean isEligibleForBulkMove() {
        return isVisibleInActiveDatedList();
    }

    public Task markDone(LocalDateTime completedAt) {
        ensureOpenForTransition("mark as done");
        LocalDateTime transitionAt = requireTransitionTime(completedAt, "completedAt");
        return new Task(
                id,
                userId,
                title,
                description,
                TaskStatus.DONE,
                plannedForAt,
                deadlineAt,
                createdAt,
                transitionAt,
                transitionAt,
                null
        );
    }

    public Task markClosed(LocalDateTime closedAt) {
        ensureOpenForTransition("close");
        LocalDateTime transitionAt = requireTransitionTime(closedAt, "closedAt");
        return new Task(
                id,
                userId,
                title,
                description,
                TaskStatus.CLOSED,
                plannedForAt,
                deadlineAt,
                createdAt,
                transitionAt,
                null,
                transitionAt
        );
    }

    public Task rescheduleTo(LocalDateTime plannedForAt, LocalDateTime updatedAt) {
        Objects.requireNonNull(plannedForAt, "plannedForAt is required when rescheduling a dated task");
        LocalDateTime changedAt = requireTransitionTime(updatedAt, "updatedAt");
        return new Task(
                id,
                userId,
                title,
                description,
                status,
                plannedForAt,
                deadlineAt,
                createdAt,
                changedAt,
                completedAt,
                closedAt
        );
    }

    public Task moveToBacklog(LocalDateTime updatedAt) {
        LocalDateTime changedAt = requireTransitionTime(updatedAt, "updatedAt");
        return new Task(
                id,
                userId,
                title,
                description,
                status,
                null,
                deadlineAt,
                createdAt,
                changedAt,
                completedAt,
                closedAt
        );
    }

    private void ensureOpenForTransition(String action) {
        if (status != TaskStatus.OPEN) {
            throw new IllegalStateException("Only open task can " + action);
        }
    }

    private static LocalDateTime requireTransitionTime(LocalDateTime timestamp, String fieldName) {
        return Objects.requireNonNull(timestamp, fieldName + " is required");
    }
}
