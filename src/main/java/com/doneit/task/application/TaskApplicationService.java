package com.doneit.task.application;

import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.application.view.TaskFormView;
import com.doneit.task.application.view.TaskListItemView;
import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskRepository;
import com.doneit.task.domain.TaskStatus;
import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
public class TaskApplicationService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TaskApplicationService(TaskRepository taskRepository, UserRepository userRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskListItemView createTask(@Valid @NotNull CreateTaskCommand command) {
        if (command.plannedForAt() == null) {
            throw new IllegalArgumentException("plannedForAt is required for a dated task");
        }

        User activeUser = requireActiveUser();
        LocalDateTime now = LocalDateTime.now(clock);
        Task task = new Task(
                null,
                activeUser.id(),
                command.title(),
                normalizeDescription(command.description()),
                TaskStatus.OPEN,
                command.plannedForAt(),
                command.deadlineAt(),
                now,
                now,
                null,
                null
        );

        return toView(taskRepository.create(task));
    }

    @Transactional
    public TaskListItemView createBacklogTask(@Valid @NotNull CreateTaskCommand command) {
        if (command.plannedForAt() != null) {
            throw new IllegalArgumentException("Backlog task must not have plannedForAt");
        }

        User activeUser = requireActiveUser();
        LocalDateTime now = LocalDateTime.now(clock);
        Task task = new Task(
                null,
                activeUser.id(),
                command.title(),
                normalizeDescription(command.description()),
                TaskStatus.OPEN,
                null,
                command.deadlineAt(),
                now,
                now,
                null,
                null
        );

        return toView(taskRepository.create(task));
    }

    @Transactional
    public TaskListItemView editTask(@Valid @NotNull EditTaskCommand command) {
        Task existingTask = getTaskOrThrow(command.taskId());
        LocalDateTime now = LocalDateTime.now(clock);

        Task updatedTask = new Task(
                existingTask.id(),
                existingTask.userId(),
                command.title(),
                normalizeDescription(command.description()),
                existingTask.status(),
                command.plannedForAt(),
                command.deadlineAt(),
                existingTask.createdAt(),
                now,
                existingTask.completedAt(),
                existingTask.closedAt()
        );

        return toView(taskRepository.update(updatedTask));
    }

    public DailyTasksView getTasksForToday() {
        return getTasksForDate(LocalDate.now(clock));
    }

    public DailyTasksView getTasksForDate(@NotNull LocalDate date) {
        User activeUser = requireActiveUser();
        LocalDate targetDate = requireDate(date);

        List<TaskListItemView> activeTasks = taskRepository.findActiveTasksForDate(activeUser.id(), targetDate).stream()
                .map(task -> TaskListItemView.from(task, targetDate))
                .toList();
        List<TaskListItemView> completedTasks = taskRepository.findCompletedOrClosedTasks(activeUser.id()).stream()
                .map(task -> TaskListItemView.from(task, targetDate))
                .toList();

        return new DailyTasksView(targetDate, activeTasks, completedTasks);
    }

    public BacklogTasksView getBacklogTasks() {
        User activeUser = requireActiveUser();
        LocalDate today = LocalDate.now(clock);
        List<TaskListItemView> backlogTasks = taskRepository.findBacklogTasks(activeUser.id()).stream()
                .map(task -> TaskListItemView.from(task, today))
                .toList();

        return new BacklogTasksView(backlogTasks);
    }

    public TaskFormView getCreateTaskForm() {
        return TaskFormView.forCreate(LocalDateTime.now(clock).withSecond(0).withNano(0));
    }

    public TaskFormView getTaskForEdit(@NotNull Long taskId) {
        Task task = getTaskOrThrow(taskId);
        return new TaskFormView(
                task.id(),
                task.title(),
                task.description() == null ? "" : task.description(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.isBacklog(),
                true
        );
    }

    @Transactional
    public TaskListItemView markTaskAsDone(@NotNull Long taskId) {
        Task task = taskRepository.markDone(requireTaskId(taskId), LocalDateTime.now(clock))
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        return toView(task);
    }

    @Transactional
    public TaskListItemView markTaskAsClosed(@NotNull Long taskId) {
        Task task = taskRepository.markClosed(requireTaskId(taskId), LocalDateTime.now(clock))
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        return toView(task);
    }

    @Transactional
    public TaskListItemView moveTask(@Valid @NotNull RescheduleTaskCommand command) {
        Task task = taskRepository.reschedule(command.taskId(), command.plannedForAt(), LocalDateTime.now(clock))
                .orElseThrow(() -> new TaskNotFoundException(command.taskId()));
        return toView(task);
    }

    @Transactional
    public TaskListItemView moveTaskToBacklog(@Valid @NotNull MoveTaskToBacklogCommand command) {
        Task task = taskRepository.moveToBacklog(command.taskId(), LocalDateTime.now(clock))
                .orElseThrow(() -> new TaskNotFoundException(command.taskId()));
        return toView(task);
    }

    @Transactional
    public int bulkMoveUnfinishedTasksToTomorrow(@NotNull LocalDate date) {
        User activeUser = requireActiveUser();
        return taskRepository.bulkMoveOpenDatedTasksToNextDay(
                activeUser.id(),
                requireDate(date),
                LocalDateTime.now(clock)
        );
    }

    private User requireActiveUser() {
        return userRepository.findActiveUser().orElseThrow(ActiveUserNotFoundException::new);
    }

    private Task getTaskOrThrow(Long taskId) {
        return taskRepository.findById(requireTaskId(taskId))
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private TaskListItemView toView(Task task) {
        return TaskListItemView.from(task, LocalDate.now(clock));
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description;
    }

    private static LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        return date;
    }

    private static Long requireTaskId(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }
        return taskId;
    }
}