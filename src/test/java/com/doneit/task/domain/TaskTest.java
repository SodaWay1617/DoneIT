package com.doneit.task.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 4, 4, 19, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 4, 4, 19, 5);

    @Test
    void taskRequiresTitle() {
        assertThrows(IllegalArgumentException.class, () -> new Task(
                1L,
                1L,
                " ",
                null,
                TaskStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 4, 5, 10, 0),
                null,
                CREATED_AT,
                UPDATED_AT,
                null,
                null
        ));
    }

    @Test
    void openTaskCanExistWithoutPlannedDateAsAlwaysActive() {
        assertDoesNotThrow(() -> new Task(
                1L,
                1L,
                "Always active",
                null,
                TaskStatus.IN_PROGRESS,
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                null,
                null
        ));
    }

    @Test
    void taskCannotHavePlannedDateAndDatetimeTogether() {
        assertThrows(IllegalArgumentException.class, () -> new Task(
                1L,
                1L,
                "Conflicting schedule",
                null,
                TaskStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 4, 5, 10, 0),
                null,
                CREATED_AT,
                UPDATED_AT,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 4, 5)
        ));
    }

    @Test
    void doneTransitionIsExplicit() {
        Task task = openTask();
        LocalDateTime completedAt = LocalDateTime.of(2026, 4, 4, 20, 0);

        Task doneTask = task.markDone(completedAt);

        assertEquals(TaskStatus.DONE, doneTask.status());
        assertEquals(completedAt, doneTask.completedAt());
        assertEquals(completedAt, doneTask.updatedAt());
    }

    @Test
    void closedTransitionIsExplicit() {
        Task task = openTask();
        LocalDateTime closedAt = LocalDateTime.of(2026, 4, 4, 20, 15);

        Task closedTask = task.markClosed(closedAt);

        assertEquals(TaskStatus.CLOSED, closedTask.status());
        assertEquals(closedAt, closedTask.closedAt());
        assertEquals(closedAt, closedTask.updatedAt());
    }

    @Test
    void completedTasksAreExcludedFromActiveList() {
        Task doneTask = openTask().markDone(LocalDateTime.of(2026, 4, 4, 21, 0));

        assertFalse(doneTask.isVisibleInActiveDatedList());
        assertFalse(doneTask.isEligibleForBulkMove());
    }

    @Test
    void backlogTasksAreExcludedFromDatedActiveListAndBulkMove() {
        Task backlogTask = openTask().moveToBacklog(LocalDateTime.of(2026, 4, 4, 21, 5));

        assertTrue(backlogTask.isBacklog());
        assertEquals(TaskStatus.BACKLOG, backlogTask.status());
        assertFalse(backlogTask.isVisibleInActiveDatedList());
        assertFalse(backlogTask.isEligibleForBulkMove());
    }

    @Test
    void undatedOpenTaskIsVisibleButNotEligibleForBulkMove() {
        Task task = new Task(
                1L,
                1L,
                "Daily work",
                null,
                TaskStatus.IN_PROGRESS,
                null,
                null,
                CREATED_AT,
                UPDATED_AT,
                null,
                null
        );

        assertFalse(task.isBacklog());
        assertTrue(task.isVisibleInActiveDatedList());
        assertFalse(task.isEligibleForBulkMove());
    }

    @Test
    void onlyOpenTasksCanChangeToDoneOrClosed() {
        Task doneTask = openTask().markDone(LocalDateTime.of(2026, 4, 4, 21, 10));

        assertThrows(IllegalStateException.class, () -> doneTask.markClosed(LocalDateTime.of(2026, 4, 4, 21, 11)));
        assertThrows(IllegalStateException.class, () -> doneTask.markDone(LocalDateTime.of(2026, 4, 4, 21, 12)));
    }

    @Test
    void datedOpenTaskRemainsEligibleForActiveListAndBulkMove() {
        Task task = openTask();

        assertTrue(task.isVisibleInActiveDatedList());
        assertTrue(task.isEligibleForBulkMove());
    }

    private static Task openTask() {
        return new Task(
                1L,
                1L,
                "Buy milk",
                "Family reminder",
                TaskStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 4, 5, 10, 0),
                LocalDateTime.of(2026, 4, 6, 18, 0),
                CREATED_AT,
                UPDATED_AT,
                null,
                null
        );
    }
}
