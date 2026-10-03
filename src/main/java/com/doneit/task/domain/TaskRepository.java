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

    List<Task> findActiveTasksDueByDate(Long userId, LocalDate date);

    List<Task> findBacklogTasks(Long userId);

    List<Task> findInboxTasks(Long userId);

    List<Task> findCompletedOrClosedTasks(Long userId);

    List<Task> findCompletedOrClosedTasksForDate(Long userId, LocalDate date);

    List<Task> findTasksForCalendarRange(Long userId, LocalDate startDate, LocalDate endDateExclusive);

    List<Task> findTasksForKanban(Long userId, LocalDate date);

    void reorderKanban(Long userId, TaskStatus status, List<Long> orderedTaskIds, LocalDateTime updatedAt);

    Optional<Task> markDone(Long taskId, LocalDateTime completedAt);

    Optional<Task> markClosed(Long taskId, LocalDateTime closedAt);

    Optional<Task> addTrackedTime(Long taskId, int minutes, LocalDateTime updatedAt);

    Optional<Task> reschedule(Long taskId, LocalDateTime plannedForAt, LocalDateTime updatedAt);

    Optional<Task> moveToBacklog(Long taskId, LocalDateTime updatedAt);

    Optional<Task> moveToProject(Long taskId, Long projectId, LocalDateTime updatedAt);

    int bulkMoveOpenDatedTasksToNextDay(Long userId, LocalDate date, LocalDateTime updatedAt);

    int bulkMoveOverdueOpenDatedTasksToDate(Long userId, LocalDate date, LocalDateTime updatedAt);
}
