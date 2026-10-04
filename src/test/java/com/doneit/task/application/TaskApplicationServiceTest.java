package com.doneit.task.application;

import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.CalendarMonthView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.application.view.KanbanTasksView;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskRepository;
import com.doneit.task.domain.TaskStatus;
import com.doneit.task.domain.TaskPriority;
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
import java.time.YearMonth;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
    void createTaskAllowsPermanentOpenTaskWithoutDate() {
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 103L));
        TaskListItemView result = service.createTask(new CreateTaskCommand("Task", null, null, null));
        assertEquals(TaskStatus.IN_PROGRESS, result.status());
        assertNull(result.plannedForAt());
    }

    @Test
    void createTaskAllowsPlannedDayWithoutTime() {
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 104L));
        CreateTaskCommand command = new CreateTaskCommand("Day task", null, null, null, null, LocalDate.of(2026, 4, 12), false);

        TaskListItemView result = service.createTask(command);

        assertEquals(LocalDate.of(2026, 4, 12), result.plannedDate());
        assertNull(result.plannedForAt());
        assertEquals(TaskStatus.IN_PROGRESS, result.status());
    }

    @Test
    void createTaskPreservesSelectedPriority() {
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 105L));
        CreateTaskCommand command = new CreateTaskCommand(
                "Urgent task", null, null, null, null, null, false, TaskPriority.CRITICAL
        );

        TaskListItemView result = service.createTask(command);

        assertEquals(TaskPriority.CRITICAL, result.priority());
    }

    @Test
    void createTaskUsesSelectedWorkflowStatus() {
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 106L));
        CreateTaskCommand command = new CreateTaskCommand(
                "Needs analysis", null, null, null, null, null, false,
                TaskPriority.NORMAL, TaskStatus.NEW
        );

        TaskListItemView result = service.createTask(command);

        assertEquals(TaskStatus.NEW, result.status());
    }

    @Test
    void createBacklogTaskCreatesUndatedOpenTask() {
        CreateTaskCommand command = new CreateTaskCommand("Someday", "Reminder", null, null);
        when(taskRepository.create(any(Task.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 102L));

        TaskListItemView result = service.createBacklogTask(command);

        assertTrue(result.backlog());
        assertEquals(TaskStatus.BACKLOG, result.status());
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
        assertFalse(result.backlog());
        verify(taskRepository).update(any(Task.class));
    }

    @Test
    void getTasksForTodayDelegatesToCurrentDate() {
        LocalDate today = LocalDate.of(2026, 4, 7);
        when(taskRepository.findActiveTasksDueByDate(ACTIVE_USER.id(), today)).thenReturn(List.of(
                openTask(1L, LocalDateTime.of(2026, 4, 7, 12, 0), null),
                openTask(2L, LocalDateTime.of(2026, 4, 6, 12, 0), null)
        ));

        DailyTasksView result = service.getTasksForToday();

        assertEquals(today, result.selectedDate());
        assertEquals(2, result.activeTasks().size());
        assertTrue(result.activeTasks().get(1).overdue());
        assertTrue(result.completedTasks().isEmpty());
    }

    @Test
    void getTasksForSelectedDateUsesRepositoryQueries() {
        LocalDate date = LocalDate.of(2026, 4, 10);
        when(taskRepository.findActiveTasksForDate(ACTIVE_USER.id(), date)).thenReturn(List.of(openTask(3L, LocalDateTime.of(2026, 4, 10, 9, 0), null)));

        DailyTasksView result = service.getTasksForDate(date);

        assertEquals(date, result.selectedDate());
        verify(taskRepository).findActiveTasksForDate(ACTIVE_USER.id(), date);
    }

    @Test
    void getTasksForPastSelectedDateIncludesOpenTasksDueByThatDate() {
        LocalDate date = LocalDate.of(2026, 4, 6);
        when(taskRepository.findActiveTasksDueByDate(ACTIVE_USER.id(), date)).thenReturn(List.of(
                openTask(3L, LocalDateTime.of(2026, 4, 4, 9, 0), null),
                openTask(4L, LocalDateTime.of(2026, 4, 6, 9, 0), null)
        ));

        DailyTasksView result = service.getTasksForDate(date);

        assertEquals(date, result.selectedDate());
        assertEquals(2, result.activeTasks().size());
        assertTrue(result.activeTasks().getFirst().overdue());
        verify(taskRepository).findActiveTasksDueByDate(ACTIVE_USER.id(), date);
    }

    @Test
    void getTasksForDateRequiresDate() {
        assertThrows(IllegalArgumentException.class, () -> service.getTasksForDate(null));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void getBacklogTasksReturnsSeparateViewModel() {
        when(taskRepository.findBacklogTasks(ACTIVE_USER.id())).thenReturn(List.of(backlogTask(4L)));

        BacklogTasksView result = service.getBacklogTasks();

        assertEquals(1, result.tasks().size());
        assertTrue(result.tasks().getFirst().backlog());
    }

    @Test
    void randomTaskForTodayReturnsActiveTaskFromTodayView() {
        LocalDate today = LocalDate.of(2026, 4, 7);
        when(taskRepository.findActiveTasksDueByDate(ACTIVE_USER.id(), today))
                .thenReturn(List.of(openTask(5L, LocalDateTime.of(2026, 4, 7, 12, 0), null)));

        Optional<TaskListItemView> result = service.getRandomTaskForToday();

        assertTrue(result.isPresent());
        assertEquals(5L, result.orElseThrow().id());
    }

    @Test
    void randomTaskForTodayReturnsEmptyWhenTodayHasNoActiveTasks() {
        LocalDate today = LocalDate.of(2026, 4, 7);
        when(taskRepository.findActiveTasksDueByDate(ACTIVE_USER.id(), today)).thenReturn(List.of());

        Optional<TaskListItemView> result = service.getRandomTaskForToday();

        assertTrue(result.isEmpty());
    }

    @Test
    void getKanbanTasksForDateSplitsTasksByColumn() {
        LocalDate date = LocalDate.of(2026, 4, 7);
        when(taskRepository.findTasksForKanban(ACTIVE_USER.id(), date))
                .thenReturn(List.of(
                        openTask(10L, LocalDateTime.of(2026, 4, 6, 9, 0), null),
                        doneTask(11L),
                        closedTask(12L)
                ));

        KanbanTasksView result = service.getKanbanTasksForDate(date);

        assertEquals(date, result.selectedDate());
        assertEquals(1, result.openTasks().size());
        assertEquals(1, result.doneTasks().size());
        assertEquals(1, result.closedTasks().size());
        assertEquals(9, result.columns().size());
        assertTrue(result.openTasks().getFirst().overdue());
        verify(taskRepository).findTasksForKanban(ACTIVE_USER.id(), date);
    }

    @Test
    void reorderKanbanPersistsCompleteColumnOrder() {
        when(taskRepository.findById(10L)).thenReturn(Optional.of(openTask(10L, null, null)));
        when(taskRepository.findById(11L)).thenReturn(Optional.of(openTask(11L, null, null)));

        service.reorderKanban(TaskStatus.TODO, List.of(11L, 10L));

        verify(taskRepository).reorderKanban(
                eq(ACTIVE_USER.id()), eq(TaskStatus.TODO), eq(List.of(11L, 10L)), any(LocalDateTime.class)
        );
    }

    @Test
    void getCalendarMonthBuildsMondayFirstGridAndDeduplicatesSameDayDeadline() {
        YearMonth month = YearMonth.of(2026, 4);
        Task plannedWithSameDayDeadline = openTask(
                20L,
                LocalDateTime.of(2026, 4, 7, 9, 0),
                LocalDateTime.of(2026, 4, 7, 18, 0)
        );
        Task separateDeadline = openTask(
                21L,
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 9, 12, 0)
        );
        when(taskRepository.findTasksForCalendarRange(
                ACTIVE_USER.id(),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 5, 4)
        )).thenReturn(List.of(plannedWithSameDayDeadline, separateDeadline));

        CalendarMonthView result = service.getCalendarMonth(month);

        assertEquals(month, result.month());
        assertEquals(LocalDate.of(2026, 3, 30), result.days().getFirst().date());
        assertEquals(LocalDate.of(2026, 5, 3), result.days().getLast().date());
        assertTrue(result.days().stream().anyMatch(day -> day.date().equals(LocalDate.of(2026, 4, 7)) && day.today()));
        assertEquals(1, result.days().stream()
                .filter(day -> day.date().equals(LocalDate.of(2026, 4, 7)))
                .findFirst()
                .orElseThrow()
                .items()
                .size());
        assertEquals(1, result.days().stream()
                .filter(day -> day.date().equals(LocalDate.of(2026, 4, 9)))
                .findFirst()
                .orElseThrow()
                .items()
                .size());
    }

    @Test
    void getCalendarMonthShowsFinishedTasksOnTheirActualFinishDate() {
        YearMonth month = YearMonth.of(2026, 4);
        when(taskRepository.findTasksForCalendarRange(
                ACTIVE_USER.id(), LocalDate.of(2026, 3, 30), LocalDate.of(2026, 5, 4)
        )).thenReturn(List.of(doneTask(30L), closedTask(31L)));

        CalendarMonthView result = service.getCalendarMonth(month);

        var items = result.days().stream()
                .filter(day -> day.date().equals(LocalDate.of(2026, 4, 7)))
                .findFirst().orElseThrow().items();
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(item -> item.occurrenceType()
                == com.doneit.task.application.view.CalendarOccurrenceType.COMPLETED));
        assertTrue(items.stream().anyMatch(item -> item.occurrenceType()
                == com.doneit.task.application.view.CalendarOccurrenceType.CLOSED));
    }

    @Test
    void markTaskAsDoneUsesRepositoryTransition() {
        when(taskRepository.markDone(eq(300L), any(LocalDateTime.class))).thenReturn(Optional.of(doneTask(300L)));

        TaskListItemView result = service.markTaskAsDone(300L);

        assertEquals(TaskStatus.DONE, result.status());
    }

    @Test
    void trackTimeAddsMinutesToExistingTotal() {
        Task task = openTask(305L, null, null);
        Task tracked = new Task(task.id(), task.userId(), task.title(), task.description(), task.status(),
                task.plannedForAt(), task.deadlineAt(), task.createdAt(), task.updatedAt(), task.completedAt(),
                task.closedAt(), task.projectId(), task.taskNumber(), task.taskKey(), task.plannedDate(),
                task.priority(), null, 45);
        when(taskRepository.findById(305L)).thenReturn(Optional.of(task));
        when(taskRepository.addTrackedTime(eq(305L), eq(ACTIVE_USER.id()), eq(45), any(LocalDateTime.class)))
                .thenReturn(Optional.of(tracked));

        TaskListItemView result = service.trackTime(305L, 45);

        assertEquals(45, result.spentMinutes());
    }

    @Test
    void markDoneCanTrackTimeInSameOperation() {
        Task done = doneTask(306L);
        Task tracked = new Task(done.id(), done.userId(), done.title(), done.description(), done.status(),
                done.plannedForAt(), done.deadlineAt(), done.createdAt(), done.updatedAt(), done.completedAt(),
                done.closedAt(), done.projectId(), done.taskNumber(), done.taskKey(), done.plannedDate(),
                done.priority(), null, 30);
        when(taskRepository.markDone(eq(306L), any(LocalDateTime.class))).thenReturn(Optional.of(done));
        when(taskRepository.addTrackedTime(eq(306L), eq(ACTIVE_USER.id()), eq(30), any(LocalDateTime.class)))
                .thenReturn(Optional.of(tracked));

        TaskListItemView result = service.markTaskAsDone(306L, 30);

        assertEquals(TaskStatus.DONE, result.status());
        assertEquals(30, result.spentMinutes());
    }

    @Test
    void markTaskAsDoneReturnsExistingTaskWhenActionWasAlreadyApplied() {
        when(taskRepository.markDone(eq(300L), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(taskRepository.findById(300L)).thenReturn(Optional.of(doneTask(300L)));

        TaskListItemView result = service.markTaskAsDone(300L);

        assertEquals(TaskStatus.DONE, result.status());
    }

    @Test
    void markTaskAsDoneRequiresId() {
        assertThrows(IllegalArgumentException.class, () -> service.markTaskAsDone(null));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void markTaskAsClosedUsesRepositoryTransition() {
        when(taskRepository.markClosed(eq(301L), any(LocalDateTime.class))).thenReturn(Optional.of(closedTask(301L)));

        TaskListItemView result = service.markTaskAsClosed(301L);

        assertEquals(TaskStatus.CLOSED, result.status());
    }

    @Test
    void markTaskAsClosedReturnsExistingTaskWhenActionWasAlreadyApplied() {
        when(taskRepository.markClosed(eq(301L), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(taskRepository.findById(301L)).thenReturn(Optional.of(closedTask(301L)));

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
        when(taskRepository.moveToBacklog(eq(303L), any(LocalDateTime.class))).thenReturn(Optional.of(backlogTask(303L)));

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
    void bulkMoveOverdueTasksUsesActiveUserAndToday() {
        LocalDate today = LocalDate.of(2026, 4, 7);
        when(taskRepository.bulkMoveOverdueOpenDatedTasksToDate(eq(ACTIVE_USER.id()), eq(today), any(LocalDateTime.class))).thenReturn(2);

        int moved = service.bulkMoveOverdueTasksToToday();

        assertEquals(2, moved);
    }

    @Test
    void bulkMoveRequiresDate() {
        assertThrows(IllegalArgumentException.class, () -> service.bulkMoveUnfinishedTasksToTomorrow(null));
        verifyNoInteractions(taskRepository);
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
                task.closedAt(), task.projectId(), task.taskNumber(), task.taskKey(), task.plannedDate(), task.priority()
        );
    }

    private static Task openTask(Long id, LocalDateTime plannedForAt, LocalDateTime deadlineAt) {
        return new Task(
                id,
                ACTIVE_USER.id(),
                "Task " + id,
                null,
                TaskStatus.IN_PROGRESS,
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

    private static Task backlogTask(Long id) {
        return new Task(
                id,
                ACTIVE_USER.id(),
                "Backlog " + id,
                null,
                TaskStatus.BACKLOG,
                null,
                null,
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                null,
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
