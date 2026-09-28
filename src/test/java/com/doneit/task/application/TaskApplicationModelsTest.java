package com.doneit.task.application;

import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.CalendarDayView;
import com.doneit.task.application.view.CalendarItemView;
import com.doneit.task.application.view.CalendarMonthView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.application.view.KanbanTasksView;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
                TaskStatus.BACKLOG,
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
    void taskListItemViewMarksPlannedDayBeforeReferenceDateAsOverdue() {
        Task task = new Task(
                12L,
                1L,
                "Day task",
                null,
                TaskStatus.OPEN,
                null,
                null,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 4, 4)
        );

        TaskListItemView view = TaskListItemView.from(task, LocalDate.of(2026, 4, 5));

        assertTrue(view.overdue());
    }

    @Test
    void taskListItemViewHidesProjectCodeWhenPreferenceIsDisabled() {
        Task task = taskWithKey("WORK-7___doneit");

        TaskListItemView view = TaskListItemView.from(task, LocalDate.of(2026, 4, 5), false);

        assertEquals("Task name", view.title());
        assertEquals("WORK-7", view.taskCode());
    }

    @Test
    void taskListItemViewShowsSanitizedDefaultProjectCodeWhenPreferenceIsEnabled() {
        Task task = taskWithKey("MAIN-doneit-9___doneit");

        TaskListItemView view = TaskListItemView.from(task, LocalDate.of(2026, 4, 5), true);

        assertEquals("MAIN-9  Task name", view.title());
        assertEquals("MAIN-9", view.taskCode());
    }

    @Test
    void dailyTasksViewDefensivelyCopiesCollections() {
        List<TaskListItemView> activeTasks = new ArrayList<>();
        activeTasks.add(new TaskListItemView(1L, "Task", null, TaskStatus.OPEN, null, null, false, false));
        DailyTasksView view = new DailyTasksView(LocalDate.of(2026, 4, 5), activeTasks, List.of());

        activeTasks.add(new TaskListItemView(2L, "Other task", null, TaskStatus.OPEN, null, null, false, false));

        assertEquals(1, view.activeTasks().size());
        assertEquals(1, view.activeTaskCount());
    }

    @Test
    void backlogTasksViewDefensivelyCopiesCollections() {
        List<TaskListItemView> source = new ArrayList<>();
        BacklogTasksView view = new BacklogTasksView(source);

        source.add(new TaskListItemView(1L, "Task", null, TaskStatus.BACKLOG, null, null, true, false));

        assertTrue(view.tasks().isEmpty());
    }

    @Test
    void kanbanTasksViewDefensivelyCopiesCollections() {
        List<TaskListItemView> openTasks = new ArrayList<>();
        KanbanTasksView view = new KanbanTasksView(LocalDate.of(2026, 4, 5), openTasks, List.of(), List.of());

        openTasks.add(new TaskListItemView(1L, "Task", null, TaskStatus.OPEN, null, null, false, false));

        assertTrue(view.openTasks().isEmpty());
    }

    @Test
    void calendarMonthViewExposesLinkValuesAndDefensivelyCopiesCollections() {
        List<CalendarDayView> days = new ArrayList<>();
        CalendarMonthView view = new CalendarMonthView(
                YearMonth.of(2026, 4),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 5, 1),
                days
        );

        days.add(new CalendarDayView(LocalDate.of(2026, 4, 1), true, false, List.of()));

        assertEquals("2026-04", view.monthValue());
        assertEquals("2026-03", view.previousMonthValue());
        assertEquals("2026-05", view.nextMonthValue());
        assertTrue(view.days().isEmpty());
    }

    @Test
    void calendarDayViewDefensivelyCopiesCollections() {
        List<CalendarItemView> items = new ArrayList<>();
        CalendarDayView view = new CalendarDayView(LocalDate.of(2026, 4, 1), true, false, items);

        items.add(new CalendarItemView(
                1L,
                null,
                LocalDate.of(2026, 4, 1),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                "Task",
                TaskStatus.OPEN,
                false
        ));

        assertTrue(view.items().isEmpty());
    }

    private static Task taskWithKey(String taskKey) {
        return new Task(
                20L,
                1L,
                "Task name",
                null,
                TaskStatus.OPEN,
                LocalDateTime.of(2026, 4, 5, 10, 0),
                null,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                null,
                null,
                2L,
                9L,
                taskKey,
                null
        );
    }
}
