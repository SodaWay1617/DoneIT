package com.doneit.task.application;

import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskApplicationModelsTest {

    @Test
    void createTaskCommandRequiresTitle() {
        assertThrows(IllegalArgumentException.class, () -> new CreateTaskCommand(" ", null, null, null));
    }

    @Test
    void editTaskCommandRequiresIdAndTitle() {
        assertThrows(IllegalArgumentException.class, () -> new EditTaskCommand(null, "Task", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new EditTaskCommand(1L, " ", null, null, null));
    }

    @Test
    void rescheduleCommandRequiresIdAndPlannedDate() {
        assertThrows(IllegalArgumentException.class, () -> new RescheduleTaskCommand(null, LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new RescheduleTaskCommand(1L, null));
    }

    @Test
    void moveToBacklogCommandRequiresId() {
        assertThrows(IllegalArgumentException.class, () -> new MoveTaskToBacklogCommand(null));
    }

    @Test
    void taskListItemViewMapsBacklogTask() {
        Task task = new Task(
                10L,
                1L,
                "Someday maybe",
                "Reminder",
                TaskStatus.OPEN,
                null,
                null,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                null,
                null
        );

        TaskListItemView view = TaskListItemView.from(task, LocalDate.of(2026, 4, 5));

        assertTrue(view.backlog());
        assertFalse(view.overdue());
        assertEquals("Someday maybe", view.title());
    }

    @Test
    void taskListItemViewMarksOpenTaskOverdueWhenDeadlinePassed() {
        Task task = new Task(
                11L,
                1L,
                "Pay bill",
                null,
                TaskStatus.OPEN,
                LocalDateTime.of(2026, 4, 5, 10, 0),
                LocalDateTime.of(2026, 4, 4, 18, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                null,
                null
        );

        TaskListItemView view = TaskListItemView.from(task, LocalDate.of(2026, 4, 5));

        assertTrue(view.overdue());
        assertFalse(view.backlog());
    }

    @Test
    void dailyTasksViewDefensivelyCopiesCollections() {
        List<TaskListItemView> activeTasks = new ArrayList<>();
        DailyTasksView view = new DailyTasksView(LocalDate.of(2026, 4, 5), activeTasks, List.of());

        activeTasks.add(new TaskListItemView(1L, "Task", null, TaskStatus.OPEN, null, null, true, false));

        assertTrue(view.activeTasks().isEmpty());
    }

    @Test
    void backlogTasksViewDefensivelyCopiesCollections() {
        List<TaskListItemView> source = new ArrayList<>();
        BacklogTasksView view = new BacklogTasksView(source);

        source.add(new TaskListItemView(1L, "Task", null, TaskStatus.OPEN, null, null, true, false));

        assertTrue(view.tasks().isEmpty());
    }
}
