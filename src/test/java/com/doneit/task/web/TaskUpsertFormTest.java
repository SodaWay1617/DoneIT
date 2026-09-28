package com.doneit.task.web;

import com.doneit.task.application.view.TaskFormView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskUpsertFormTest {

    @Test
    void mapsUndatedOpenTaskAsPermanent() {
        TaskFormView view = new TaskFormView(
                1L, "Routine", "", null, null, false, true, 2L, null
        );

        TaskUpsertForm form = TaskUpsertForm.from(view);

        assertTrue(form.isPermanent());
        assertFalse(form.isBacklog());
    }

    @Test
    void doesNotMapBacklogTaskAsPermanent() {
        TaskFormView view = new TaskFormView(
                1L, "Someday", "", null, null, true, true, 2L, null
        );

        TaskUpsertForm form = TaskUpsertForm.from(view);

        assertTrue(form.isBacklog());
        assertFalse(form.isPermanent());
    }
}
