package com.doneit.task.web;

import com.doneit.task.application.view.TaskFormView;
import com.doneit.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskUpsertFormTest {

    @Test
    void mapsUndatedOpenTaskWithBothDateOptionsDisabled() {
        TaskFormView view = new TaskFormView(
                1L, "Routine", "", null, null, false, true, 2L, null
        );

        TaskUpsertForm form = TaskUpsertForm.from(view);

        assertTrue(form.isWithoutPlannedDate());
        assertTrue(form.isWithoutDeadline());
        assertFalse(form.isBacklog());
        assertTrue(form.getStatus() == TaskStatus.IN_PROGRESS);
    }

    @Test
    void mapsExistingUndatedBacklogTaskWithDateOptionsDisabled() {
        TaskFormView view = new TaskFormView(
                1L, "Someday", "", null, null, true, true, 2L, null
        );

        TaskUpsertForm form = TaskUpsertForm.from(view);

        assertTrue(form.isBacklog());
        assertTrue(form.isWithoutPlannedDate());
        assertTrue(form.isWithoutDeadline());
        assertTrue(form.getStatus() == TaskStatus.BACKLOG);
    }

    @Test
    void newTaskFormDefaultsToNewStatus() {
        TaskUpsertForm form = TaskUpsertForm.from(TaskFormView.forCreate(null));

        assertTrue(form.getStatus() == TaskStatus.NEW);
        assertFalse(form.isWithoutPlannedDate());
        assertFalse(form.isWithoutDeadline());
    }

    @Test
    void mapsEstimateFromExistingTask() {
        TaskFormView view = new TaskFormView(
                1L, "Estimated", "", null, null, false, true, 2L, null,
                com.doneit.task.domain.TaskPriority.NORMAL, TaskStatus.IN_PROGRESS, 90
        );

        TaskUpsertForm form = TaskUpsertForm.from(view);

        org.junit.jupiter.api.Assertions.assertEquals(90, form.getEstimateMinutes());
    }

    @Test
    void newTaskDefaultsToDateOnlyPlanning() {
        var initial = java.time.LocalDateTime.of(2026, 10, 4, 12, 30);

        TaskUpsertForm form = TaskUpsertForm.from(TaskFormView.forCreate(initial));

        assertFalse(form.isPlannedWithTime());
        assertEquals(initial.toLocalDate(), form.getPlannedDate());
        assertEquals(null, form.getPlannedForAt());
    }

    @Test
    void existingTimedTaskKeepsExactTimeMode() {
        var planned = java.time.LocalDateTime.of(2026, 10, 4, 12, 30);
        TaskFormView view = new TaskFormView(
                1L, "Timed", "", planned, null, false, true, 2L, null
        );

        TaskUpsertForm form = TaskUpsertForm.from(view);

        assertTrue(form.isPlannedWithTime());
        assertEquals(planned, form.getPlannedForAt());
    }
}
