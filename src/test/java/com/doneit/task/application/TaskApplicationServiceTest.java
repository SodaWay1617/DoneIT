package com.doneit.task.application;

import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskRepository;
import com.doneit.task.domain.TaskStatus;
import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskApplicationServiceTest {

    private static final User ACTIVE_USER = new User(
            7L,
            "doneit",
            "hash",
            "DoneIt",
            LocalDateTime.of(2026, 4, 1, 9, 0),
            LocalDateTime.of(2026, 4, 1, 9, 0)
    );

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-04-07T08:15:00Z"),
            ZoneId.of("Europe/Moscow")
    );

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private TaskApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TaskApplicationService(taskRepository, userRepository, FIXED_CLOCK);
        lenient().when(userRepository.findActiveUser()).thenReturn(Optional.of(ACTIVE_USER));
    }

    @Test
    void createTaskCreatesOpenDatedTask() {
        CreateTaskCommand command = new CreateTaskCommand(
                "Call mom",
                "In the evening",
                LocalDateTime.of(2026, 4, 8, 19, 0),
                LocalDateTime.of(2026, 4, 8, 21, 0)
        );
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 101L));

        TaskListItemView result = service.createTask(command);

        assertEquals(101L, result.id());
        assertFalse(result.backlog());
        verify(taskRepository).create(any(Task.class));
    }

    @Test
    void createTaskRequiresPlannedDate() {
        assertThrows(IllegalArgumentException.class, () -> service.createTask(new CreateTaskCommand("Task", null, null, null)));
    }

    @Test
    void createBacklogTaskCreatesUndatedOpenTask() {
        CreateTaskCommand command = new CreateTaskCommand("Someday", "Reminder", null, null);
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 102L));

        TaskListItemView result = service.createBacklogTask(command);

        assertTrue(result.backlog());
        assertEquals(102L, result.id());
    }

    @Test
    void editTaskPreservesTaskIdentityAndStatus() {
        Task existingTask = openTask(200L, LocalDateTime.of(2026, 4, 9, 10, 0), LocalDateTime.of(2026, 4, 10, 12, 0));
        when(taskRepository.findById(200L)).thenReturn(Optional.of(existingTask));
        when(taskRepository.update(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskListItemView result = service.editTask(new EditTaskCommand(
                200L,
                "Updated title",
                "Updated description",
                null,
                LocalDateTime.of(2026, 4, 11, 18, 0)
        ));

        assertEquals("Updated title", result.title());
        assertTrue(result.backlog());
        verify(taskRepository).update(any(Task.class));
    }

    @Test
    void getTasksForTodayDelegatesToCurrentDate() {
        LocalDate today = LocalDate.of(2026, 4, 7);
        when(taskRepository.findActiveTasksForDate(ACTIVE_USER.id(), today)).thenReturn(List.of(openTask(1L, LocalDateTime.of(2026, 4, 7, 12, 0), null)));
        when(taskRepository.findCompletedOrClosedTasks(ACTIVE_USER.id())).thenReturn(List.of(doneTask(2L)));

        DailyTasksView result = service.getTasksForToday();

        assertEquals(today, result.selectedDate());
        assertEquals(1, result.activeTasks().size());
        assertEquals(1, result.completedTasks().size());
    }

    @Test
    void getTasksForSelectedDateUsesRepositoryQueries() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        when(taskRepository.findActiveTasksForDate(ACTIVE_USER.id(), date)).thenReturn(List.of(openTask(3L, LocalDateTime.of(2026, 4, 10, 9, 0), null)));
        when(taskRepository.findCompletedOrClosedTasks(ACTIVE_USER.id())).thenReturn(List.of());

        DailyTasksView result = service.getTasksForDate(date);

        assertEquals(date, result.selectedDate());
        verify(taskRepository).findActiveTasksForDate(ACTIVE_USER.id(), date);
    }

    @Test
    void getBacklogTasksReturnsSeparateViewModel() {
        when(taskRepository.findBacklogTasks(ACTIVE_USER.id())).thenReturn(List.of(openTask(4L, null, null)));

        BacklogTasksView result = service.getBacklogTasks();

        assertEquals(1, result.tasks().size());
        assertTrue(result.tasks().getFirst().backlog());
    }

    @Test
    void markTaskAsDoneUsesRepositoryTransition() {
        when(taskRepository.markDone(eq(300L), any(LocalDateTime.class))).thenReturn(Optional.of(doneTask(300L)));

        TaskListItemView result = service.markTaskAsDone(300L);

        assertEquals(TaskStatus.DONE, result.status());
    }

    @Test
    void markTaskAsClosedUsesRepositoryTransition() {
        when(taskRepository.markClosed(eq(301L), any(LocalDateTime.class))).thenReturn(Optional.of(closedTask(301L)));

        TaskListItemView result = service.markTaskAsClosed(301L);

        assertEquals(TaskStatus.CLOSED, result.status());
    }

    @Test
    void moveTaskReschedulesTask() {
        when(taskRepository.reschedule(eq(302L), eq(LocalDateTime.of(2026, 4, 12, 14, 30)), any(LocalDateTime.class)))
                .thenReturn(Optional.of(openTask(302L, LocalDateTime.of(2026, 4, 12, 14, 30), null)));

        TaskListItemView result = service.moveTask(new RescheduleTaskCommand(302L, LocalDateTime.of(2026, 4, 12, 14, 30)));

        assertEquals(LocalDateTime.of(2026, 4, 12, 14, 30), result.plannedForAt());
    }

    @Test
    void moveTaskToBacklogClearsPlannedDate() {
        when(taskRepository.moveToBacklog(eq(303L), any(LocalDateTime.class))).thenReturn(Optional.of(openTask(303L, null, null)));

        TaskListItemView result = service.moveTaskToBacklog(new MoveTaskToBacklogCommand(303L));

        assertTrue(result.backlog());
        assertNull(result.plannedForAt());
    }

    @Test
    void bulkMoveUsesActiveUserAndTargetDate() {
        LocalDate date = LocalDate.of(2026, 4, 7);
        when(taskRepository.bulkMoveOpenDatedTasksToNextDay(eq(ACTIVE_USER.id()), eq(date), any(LocalDateTime.class))).thenReturn(3);

        int moved = service.bulkMoveUnfinishedTasksToTomorrow(date);

        assertEquals(3, moved);
    }

    @Test
    void throwsMeaningfulExceptionWhenActiveUserMissing() {
        when(userRepository.findActiveUser()).thenReturn(Optional.empty());

        assertThrows(ActiveUserNotFoundException.class, () -> service.getTasksForToday());
    }

    @Test
    void throwsMeaningfulExceptionWhenTaskMissing() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> service.editTask(new EditTaskCommand(999L, "Missing", null, null, null)));
    }

    private static Task withId(Task task, Long id) {
        return new Task(
                id,
                task.userId(),
                task.title(),
                task.description(),
                task.status(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.createdAt(),
                task.updatedAt(),
                task.completedAt(),
                task.closedAt()
        );
    }

    private static Task openTask(Long id, LocalDateTime plannedForAt, LocalDateTime deadlineAt) {
        return new Task(
                id,
                ACTIVE_USER.id(),
                "Task " + id,
                null,
                TaskStatus.OPEN,
                plannedForAt,
                deadlineAt,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                null,
                null
        );
    }

    private static Task doneTask(Long id) {
        return new Task(
                id,
                ACTIVE_USER.id(),
                "Done " + id,
                null,
                TaskStatus.DONE,
                LocalDateTime.of(2026, 4, 7, 10, 0),
                null,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 7, 11, 0),
                LocalDateTime.of(2026, 4, 7, 11, 0),
                null
        );
    }

    private static Task closedTask(Long id) {
        return new Task(
                id,
                ACTIVE_USER.id(),
                "Closed " + id,
                null,
                TaskStatus.CLOSED,
                LocalDateTime.of(2026, 4, 7, 10, 0),
                null,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 7, 12, 0),
                null,
                LocalDateTime.of(2026, 4, 7, 12, 0)
        );
    }
}