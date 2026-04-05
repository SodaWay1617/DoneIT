package com.doneit.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    Task create(Task task);

    Task update(Task task);

    Optional<Task> findById(Long id);

    List<Task> findActiveTasksForDate(Long userId, LocalDate date);

    List<Task> findBacklogTasks(Long userId);

    List<Task> findCompletedOrClosedTasks(Long userId);

    Optional<Task> markDone(Long taskId, LocalDateTime completedAt);

    Optional<Task> markClosed(Long taskId, LocalDateTime closedAt);

    Optional<Task> reschedule(Long taskId, LocalDateTime plannedForAt, LocalDateTime updatedAt);

    Optional<Task> moveToBacklog(Long taskId, LocalDateTime updatedAt);

    int bulkMoveOpenDatedTasksToNextDay(Long userId, LocalDate date, LocalDateTime updatedAt);
}
