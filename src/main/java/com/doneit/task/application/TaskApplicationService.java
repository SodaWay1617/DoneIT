package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Validated
@Transactional(readOnly = true)
public class TaskApplicationService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ProjectService projectService;

    public TaskApplicationService(TaskRepository taskRepository, UserRepository userRepository, Clock clock) {
        this(taskRepository,userRepository,clock,null);
    }

    @Autowired
    public TaskApplicationService(TaskRepository taskRepository, UserRepository userRepository, Clock clock, ProjectService projectService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.projectService = projectService;
    }

    @Transactional
    public TaskListItemView createTask(@Valid @NotNull CreateTaskCommand command) {
        if (command.plannedForAt() != null && command.plannedDate() != null) {
            throw new IllegalArgumentException("Choose either planned date or datetime");
        }

        User activeUser = requireActiveUser();
        requireProjectAccess(command.projectId());
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
                null,
                command.projectId(), null, null, command.plannedDate(), command.priority()
        );

        return toView(taskRepository.create(task));
    }

    @Transactional
    public TaskListItemView createBacklogTask(@Valid @NotNull CreateTaskCommand command) {
        if (command.plannedForAt() != null || command.plannedDate() != null) {
            throw new IllegalArgumentException("Backlog task must not have a planned date or datetime");
        }

        User activeUser = requireActiveUser();
        requireProjectAccess(command.projectId());
        LocalDateTime now = LocalDateTime.now(clock);
        Task task = new Task(
                null,
                activeUser.id(),
                command.title(),
                normalizeDescription(command.description()),
                TaskStatus.BACKLOG,
                null,
                command.deadlineAt(),
                now,
                now,
                null,
                null,
                command.projectId(), null, null, null, command.priority()
        );

        return toView(taskRepository.create(task));
    }

    @Transactional
    public TaskListItemView editTask(@Valid @NotNull EditTaskCommand command) {
        if (command.plannedForAt() != null && command.plannedDate() != null) throw new IllegalArgumentException(\u0022Choose either planned date or datetime\u0022);
        Task existingTask = getTaskOrThrow(command.taskId());
        requireProjectAccess(command.projectId());
        LocalDateTime now = LocalDateTime.now(clock);

        Task updatedTask = new Task(
                existingTask.id(),
                existingTask.userId(),
                command.title(),
                normalizeDescription(command.description()),
                statusForEdit(existingTask, command.backlog()),
                command.plannedForAt(),
                command.deadlineAt(),
                existingTask.createdAt(),
                now,
                existingTask.completedAt(),
                existingTask.closedAt(),
                command.projectId() == null ? existingTask.projectId() : command.projectId(),
                existingTask.taskNumber(), existingTask.taskKey(), command.backlog() ? null : command.plannedDate(),
                command.priority()
        );

        return toView(taskRepository.update(updatedTask));
    }

    public DailyTasksView getTasksForToday() {
        return getTasksForToday(null);
    }

    public DailyTasksView getTasksForToday(Long projectId) {
        User activeUser = requireActiveUser();
        LocalDate today = LocalDate.now(clock);

        List<TaskListItemView> activeTasks = taskRepository.findActiveTasksDueByDate(activeUser.id(), today).stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, today, activeUser.showProjectInTaskTitle()))
                .toList();
        List<TaskListItemView> completedTasks = taskRepository.findCompletedOrClosedTasks(activeUser.id()).stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, today, activeUser.showProjectInTaskTitle()))
                .toList();

        return new DailyTasksView(today, activeTasks, completedTasks);
    }

    public DailyTasksView getTasksForDate(@NotNull LocalDate date) {
        return getTasksForDate(date, null);
    }

    public DailyTasksView getTasksForDate(@NotNull LocalDate date, Long projectId) {
        User activeUser = requireActiveUser();
        LocalDate targetDate = requireDate(date);

        List<Task> activeTaskSource = findActiveTasksForScreen(activeUser.id(), targetDate);
        List<TaskListItemView> activeTasks = activeTaskSource.stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, targetDate, activeUser.showProjectInTaskTitle()))
                .toList();
        List<TaskListItemView> completedTasks = taskRepository.findCompletedOrClosedTasks(activeUser.id()).stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, targetDate, activeUser.showProjectInTaskTitle()))
                .toList();

        return new DailyTasksView(targetDate, activeTasks, completedTasks);
    }

    public BacklogTasksView getBacklogTasks() {
        return getBacklogTasks(null);
    }

    public BacklogTasksView getBacklogTasks(Long projectId) {
        User activeUser = requireActiveUser();
        LocalDate today = LocalDate.now(clock);
        List<TaskListItemView> backlogTasks = taskRepository.findBacklogTasks(activeUser.id()).stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, today, activeUser.showProjectInTaskTitle()))
                .toList();

        return new BacklogTasksView(backlogTasks);
    }

    public Optional<TaskListItemView> getRandomTaskForToday() {
        List<TaskListItemView> activeTasks = getTasksForToday().activeTasks();
        if (activeTasks.isEmpty()) {
            return Optional.empty();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(activeTasks.size());
        return Optional.of(activeTasks.get(randomIndex));
    }

    public KanbanTasksView getKanbanTasksForDate(@NotNull LocalDate date) {
        return getKanbanTasksForDate(date, null);
    }

    public KanbanTasksView getKanbanTasksForDate(@NotNull LocalDate date, Long projectId) {
        User activeUser = requireActiveUser();
        LocalDate targetDate = requireDate(date);

        List<TaskListItemView> activeTasks = findActiveTasksForScreen(activeUser.id(), targetDate).stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, targetDate, activeUser.showProjectInTaskTitle()))
                .toList();
        List<TaskListItemView> finishedTasks = taskRepository.findCompletedOrClosedTasksForDate(activeUser.id(), targetDate).stream().filter(t -> matches(t, projectId))
                .map(task -> TaskListItemView.from(task, targetDate, activeUser.showProjectInTaskTitle()))
                .toList();

        return new KanbanTasksView(
                targetDate,
                activeTasks,
                finishedTasks.stream().filter(task -> task.status() == TaskStatus.DONE).toList(),
                finishedTasks.stream().filter(task -> task.status() == TaskStatus.CLOSED).toList()
        );
    }

    public CalendarMonthView getCalendarMonth(@NotNull YearMonth month) {
        return getCalendarMonth(month, null);
    }

    public CalendarMonthView getCalendarMonth(@NotNull YearMonth month, Long projectId) {
        User activeUser = requireActiveUser();
        YearMonth targetMonth = Objects.requireNonNull(month, "month is required");
        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEndExclusive = targetMonth.plusMonths(1).atDay(1);
        LocalDate gridStart = monthStart.minusDays(daysSinceMonday(monthStart));
        LocalDate gridEndExclusive = monthEndExclusive.plusDays(daysUntilSunday(monthEndExclusive.minusDays(1)));

        Map<LocalDate, List<CalendarItemView>> itemsByDate = taskRepository
                .findTasksForCalendarRange(activeUser.id(), gridStart, gridEndExclusive)
                .stream()
                .filter(t -> matches(t, projectId))
                .flatMap(task -> calendarItems(task, activeUser.showProjectInTaskTitle()).stream())
                .collect(Collectors.groupingBy(CalendarItemView::date));

        LocalDate today = LocalDate.now(clock);
        List<CalendarDayView> days = new ArrayList<>();
        for (LocalDate date = gridStart; date.isBefore(gridEndExclusive); date = date.plusDays(1)) {
            List<CalendarItemView> items = itemsByDate.getOrDefault(date, List.of()).stream()
                    .sorted(Comparator.comparing(CalendarItemView::dateTime).thenComparing(CalendarItemView::taskId))
                    .toList();
            days.add(new CalendarDayView(date, YearMonth.from(date).equals(targetMonth), date.equals(today), items));
        }

        return new CalendarMonthView(
                targetMonth,
                targetMonth.minusMonths(1).atDay(1),
                targetMonth.plusMonths(1).atDay(1),
                days
        );
    }

    public TaskFormView getCreateTaskForm() {
        return TaskFormView.forCreate(null);
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
                true,
                task.projectId(),
                task.plannedDate(),
                task.priority()
        );
    }

    @Transactional
    public TaskListItemView markTaskAsDone(@NotNull Long taskId) {
        if (projectService != null) getTaskOrThrow(taskId);
        Task task = taskRepository.markDone(requireTaskId(taskId), LocalDateTime.now(clock))
                .orElseGet(() -> getExistingTaskForStatusAction(taskId));
        return toView(task);
    }

    @Transactional
    public TaskListItemView markTaskAsClosed(@NotNull Long taskId) {
        if (projectService != null) getTaskOrThrow(taskId);
        Task task = taskRepository.markClosed(requireTaskId(taskId), LocalDateTime.now(clock))
                .orElseGet(() -> getExistingTaskForStatusAction(taskId));
        return toView(task);
    }

    @Transactional
    public TaskListItemView moveTask(@Valid @NotNull RescheduleTaskCommand command) {
        if (projectService != null) getTaskOrThrow(command.taskId());
        Task task = taskRepository.reschedule(command.taskId(), command.plannedForAt(), LocalDateTime.now(clock))
                .orElseThrow(() -> new TaskNotFoundException(command.taskId()));
        return toView(task);
    }

    @Transactional
    public TaskListItemView moveTaskToBacklog(@Valid @NotNull MoveTaskToBacklogCommand command) {
        if (projectService != null) getTaskOrThrow(command.taskId());
        Task task = taskRepository.moveToBacklog(command.taskId(), LocalDateTime.now(clock))
                .orElseThrow(() -> new TaskNotFoundException(command.taskId()));
        return toView(task);
    }

    @Transactional
    public TaskListItemView moveTaskToProject(Long taskId, Long projectId) {
        getTaskOrThrow(taskId);
        requireProjectAccess(projectId);
        return toView(taskRepository.moveToProject(taskId, projectId, LocalDateTime.now(clock)).orElseThrow(() -> new TaskNotFoundException(taskId)));
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

    @Transactional
    public int bulkMoveOverdueTasksToToday() {
        User activeUser = requireActiveUser();
        return taskRepository.bulkMoveOverdueOpenDatedTasksToDate(
                activeUser.id(),
                LocalDate.now(clock),
                LocalDateTime.now(clock)
        );
    }

    private List<Task> findActiveTasksForScreen(Long userId, LocalDate targetDate) {
        LocalDate today = LocalDate.now(clock);
        if (targetDate.isAfter(today)) {
            return taskRepository.findActiveTasksForDate(userId, targetDate);
        }
        return taskRepository.findActiveTasksDueByDate(userId, targetDate);
    }

    private static List<CalendarItemView> calendarItems(Task task, boolean showProjectInTitle) {
        List<CalendarItemView> items = new ArrayList<>();
        if (task.plannedForAt() != null) {
            items.add(CalendarItemView.planned(task, showProjectInTitle));
        } else if (task.plannedDate() != null) {
            items.add(CalendarItemView.plannedDate(task, showProjectInTitle));
        }
        if (task.deadlineAt() != null && (task.plannedForAt() == null
                || !task.deadlineAt().toLocalDate().equals(task.plannedForAt().toLocalDate()))
                && (task.plannedDate() == null || !task.deadlineAt().toLocalDate().equals(task.plannedDate()))) {
            items.add(CalendarItemView.deadline(task, showProjectInTitle));
        }
        return items;
    }

    private static int daysSinceMonday(LocalDate date) {
        return date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
    }

    private static int daysUntilSunday(LocalDate date) {
        return DayOfWeek.SUNDAY.getValue() - date.getDayOfWeek().getValue();
    }

    private User requireActiveUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Optional<User> authenticated = userRepository.findByLogin(authentication.getName());
            if (authenticated.isPresent()) return authenticated.get();
        }
        return userRepository.findActiveUser().orElseThrow(ActiveUserNotFoundException::new);
    }

    private Task getTaskOrThrow(Long taskId) {
        Task task = taskRepository.findById(requireTaskId(taskId))
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        if (projectService != null && !projectService.canAccess(task.projectId())) throw new TaskNotFoundException(taskId);
        return task;
    }

    private Task getExistingTaskForStatusAction(Long taskId) {
        return getTaskOrThrow(taskId);
    }

    private TaskListItemView toView(Task task) {
        return TaskListItemView.from(task, LocalDate.now(clock), requireActiveUser().showProjectInTaskTitle());
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description;
    }

    private static TaskStatus statusForEdit(Task existingTask, boolean backlog) {
        if (existingTask.status() == TaskStatus.OPEN || existingTask.status() == TaskStatus.BACKLOG) {
            return backlog ? TaskStatus.BACKLOG : TaskStatus.OPEN;
        }
        return existingTask.status();
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

    private static boolean matches(Task task, Long projectId) {
        return projectId == null || projectId.equals(task.projectId());
    }

    private void requireProjectAccess(Long projectId) {
        if (projectId != null && projectService != null && !projectService.canAccess(projectId)) throw new IllegalArgumentException(\u0022Project is not accessible\u0022);
    }
}
