package com.doneit.task.web;

import com.doneit.assignment.application.TaskAssignmentService;
import com.doneit.project.application.ProjectService;
import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.SubtaskService;
import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.DailyTasksView;
import com.doneit.task.domain.TaskStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Controller
public class HomeController {

    private final TaskApplicationService taskApplicationService;
    private final ProjectService projectService;
    private final TaskAssignmentService taskAssignments;
    private final SubtaskService subtasks;

    public HomeController(TaskApplicationService taskApplicationService) {
        this(taskApplicationService, null, null, null);
    }

    public HomeController(TaskApplicationService taskApplicationService, ProjectService projectService) {
        this(taskApplicationService, projectService, null, null);
    }

    public HomeController(TaskApplicationService taskApplicationService, ProjectService projectService,
                          TaskAssignmentService taskAssignments) {
        this(taskApplicationService, projectService, taskAssignments, null);
    }

    @Autowired
    public HomeController(TaskApplicationService taskApplicationService, ProjectService projectService,
                          TaskAssignmentService taskAssignments, SubtaskService subtasks) {
        this.taskApplicationService = taskApplicationService;
        this.projectService = projectService;
        this.taskAssignments = taskAssignments;
        this.subtasks = subtasks;
    }

    @ModelAttribute
    public void projectContext(@RequestParam(required=false) Long projectId, Model model) {
        if (projectService != null) model.addAttribute(\u0022projects\u0022, projectService.list());
        model.addAttribute(\u0022selectedProjectId\u0022, projectId);
    }

    @GetMapping("/")
    public String today(@RequestParam(required=false) Long projectId, Model model, Principal principal) {
        DailyTasksView dailyTasksView = taskApplicationService.getTasksForToday(projectId);
        BacklogTasksView backlogTasksView = taskApplicationService.getBacklogTasks(projectId);
        populateDailyModel(model, principal, dailyTasksView, backlogTasksView,
                taskApplicationService.getInboxTasks(projectId), taskApplicationService.getFinishedTasks(projectId), true);
        return "tasks";
    }

    @GetMapping("/tasks")
    public String tasksForDate(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required=false) Long projectId,
                               Model model,
                               Principal principal) {
        DailyTasksView dailyTasksView = taskApplicationService.getTasksForDate(date,projectId);
        BacklogTasksView backlogTasksView = taskApplicationService.getBacklogTasks(projectId);
        populateDailyModel(model, principal, dailyTasksView, backlogTasksView,
                taskApplicationService.getInboxTasks(projectId), taskApplicationService.getFinishedTasks(projectId), false);
        return "tasks";
    }

    @GetMapping("/backlog")
    public String backlog(@RequestParam(required=false) Long projectId,Model model, Principal principal) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("backlog", taskApplicationService.getBacklogTasks(projectId));
        return "backlog";
    }

    @GetMapping("/inbox")
    public String inbox(@RequestParam(required=false) Long projectId, Model model, Principal principal) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("taskCollection", taskApplicationService.getInboxTasks(projectId));
        model.addAttribute("pageTitle", "Inbox");
        model.addAttribute("pageNote", "New tasks waiting for triage.");
        model.addAttribute("listKind", "inbox");
        return "status-list";
    }

    @GetMapping("/finished")
    public String finished(@RequestParam(required=false) Long projectId, Model model, Principal principal) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("taskCollection", taskApplicationService.getFinishedTasks(projectId));
        model.addAttribute("pageTitle", "Done and closed");
        model.addAttribute("pageNote", "Completed and cancelled tasks.");
        model.addAttribute("listKind", "finished");
        return "status-list";
    }

    @GetMapping("/kanban")
    public String kanban(@RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         Model model,
                         Principal principal,@RequestParam(required=false) Long projectId) {
        LocalDate selectedDate = date == null ? taskApplicationService.getTasksForToday().selectedDate() : date;
        model.addAttribute("username", principal.getName());
        model.addAttribute("kanban", taskApplicationService.getKanbanTasksForDate(selectedDate,projectId));
        return "kanban";
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(value = "month", required = false) String month,
                           Model model,
                           Principal principal,@RequestParam(required=false) Long projectId) {
        YearMonth selectedMonth = month == null || month.isBlank()
                ? YearMonth.from(taskApplicationService.getTasksForToday().selectedDate())
                : YearMonth.parse(month);
        model.addAttribute("username", principal.getName());
        model.addAttribute("calendar", taskApplicationService.getCalendarMonth(selectedMonth,projectId));
        return "calendar";
    }

    @PostMapping("/kanban/reorder")
    @ResponseBody
    public void reorderKanban(@RequestParam TaskStatus status, @RequestParam List<Long> taskIds) {
        taskApplicationService.reorderKanban(status, taskIds);
    }

    @GetMapping("/tasks/new")
    public String createTaskPage(Model model, Principal principal) {
        populateTaskFormModel(model, principal, TaskUpsertForm.from(taskApplicationService.getCreateTaskForm()));
        model.addAttribute("pageTitle", "Create Task");
        model.addAttribute("submitLabel", "Create task");
        model.addAttribute("formAction", "/tasks");
        return "task-form";
    }

    @GetMapping("/tasks/{parentId}/subtasks/new")
    public String createSubtaskPage(@PathVariable Long parentId, Model model, Principal principal) {
        var parent = taskApplicationService.getTaskForEdit(parentId);
        TaskUpsertForm form = TaskUpsertForm.from(taskApplicationService.getCreateTaskForm());
        form.setProjectId(parent.projectId());
        populateTaskFormModel(model, principal, form);
        model.addAttribute("pageTitle", "Create subtask");
        model.addAttribute("submitLabel", "Create subtask");
        model.addAttribute("formAction", "/tasks");
        model.addAttribute("parentTaskId", parentId);
        model.addAttribute("parentTaskTitle", parent.title());
        return "task-form";
    }

    @PostMapping("/tasks")
    public String createTask(@Valid @ModelAttribute("form") TaskUpsertForm form,
                             BindingResult bindingResult,
                             Model model,
                             Principal principal,
                             @RequestParam(required = false) Long parentTaskId,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateTaskFormModel(model, principal, form);
            if (parentTaskId != null) {
                model.addAttribute("parentTaskId", parentTaskId);
                model.addAttribute("parentTaskTitle", taskApplicationService.getTaskForEdit(parentTaskId).title());
            }
            model.addAttribute("pageTitle", "Create Task");
            model.addAttribute("submitLabel", "Create task");
            model.addAttribute("formAction", "/tasks");
            return "task-form";
        }

        boolean backlog = form.getStatus() == com.doneit.task.domain.TaskStatus.BACKLOG;
        boolean undatedStatus = hasNoDates(form.getStatus());
        boolean withoutPlannedDate = undatedStatus || form.isWithoutPlannedDate();
        boolean withoutDeadline = undatedStatus || form.isWithoutDeadline();
        LocalDateTime plannedForAt = withoutPlannedDate || !form.isPlannedWithTime() ? null : form.getPlannedForAt();
        LocalDate plannedDate = withoutPlannedDate || form.isPlannedWithTime() ? null : form.getPlannedDate();
        CreateTaskCommand command = new CreateTaskCommand(
                form.getTitle(),
                form.getDescription(),
                plannedForAt,
                withoutDeadline ? null : form.getDeadlineAt(), form.getProjectId(),
                plannedDate, backlog, form.getPriority(), form.getStatus(),
                form.getEstimateMinutes()
        );

        if (backlog) {
            var created = taskApplicationService.createBacklogTask(command);
            if (parentTaskId != null && subtasks != null) subtasks.attach(created.id(), parentTaskId);
            redirectAttributes.addFlashAttribute("flashMessage", "Task added to backlog.");
            return parentTaskId == null ? "redirect:/backlog" : "redirect:/tasks/" + parentTaskId + "/edit";
        }

        var created = taskApplicationService.createTask(command);
        if (parentTaskId != null && subtasks != null) subtasks.attach(created.id(), parentTaskId);
        redirectAttributes.addFlashAttribute("flashMessage", "Task created.");
        if (parentTaskId != null) return "redirect:/tasks/" + parentTaskId + "/edit";
        String statusRedirect = statusListRedirect(form.getStatus());
        if (statusRedirect != null) return "redirect:" + statusRedirect;
        LocalDate redirectDate = plannedDate != null ? plannedDate : plannedForAt == null ? null : plannedForAt.toLocalDate();
        return "redirect:" + (redirectDate == null ? "/" : resolveDateRedirect(redirectDate));
    }

    @GetMapping("/tasks/{taskId}/edit")
    public String editTaskPage(@PathVariable Long taskId, Model model, Principal principal) {
        populateTaskFormModel(model, principal, TaskUpsertForm.from(taskApplicationService.getTaskForEdit(taskId)));
        if (taskAssignments != null) {
            var assignment = taskAssignments.assignmentForTask(taskId);
            model.addAttribute("assignmentMembers", assignment.members());
            model.addAttribute("assignedUserIds", assignment.assignedUserIds());
        }
        if (subtasks != null) {
            model.addAttribute("subtasks", subtasks.summary(taskId));
            model.addAttribute("parentTask", subtasks.parentOf(taskId));
        }
        model.addAttribute("pageTitle", "Edit Task");
        model.addAttribute("submitLabel", "Save changes");
        model.addAttribute("formAction", "/tasks/" + taskId);
        return "task-form";
    }

    @PostMapping("/tasks/{taskId}")
    public String editTask(@PathVariable Long taskId,
                           @Valid @ModelAttribute("form") TaskUpsertForm form,
                           BindingResult bindingResult,
                           Model model,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            form.setTaskId(taskId);
            form.setEditMode(true);
            populateTaskFormModel(model, principal, form);
            model.addAttribute("pageTitle", "Edit Task");
            model.addAttribute("submitLabel", "Save changes");
            model.addAttribute("formAction", "/tasks/" + taskId);
            return "task-form";
        }

        boolean backlog = form.getStatus() == com.doneit.task.domain.TaskStatus.BACKLOG;
        boolean undatedStatus = hasNoDates(form.getStatus());
        boolean withoutPlannedDate = undatedStatus || form.isWithoutPlannedDate();
        boolean withoutDeadline = undatedStatus || form.isWithoutDeadline();
        LocalDateTime plannedForAt = withoutPlannedDate || !form.isPlannedWithTime() ? null : form.getPlannedForAt();
        LocalDate plannedDate = withoutPlannedDate || form.isPlannedWithTime() ? null : form.getPlannedDate();
        EditTaskCommand command = new EditTaskCommand(
                taskId,
                form.getTitle(),
                form.getDescription(),
                plannedForAt,
                withoutDeadline ? null : form.getDeadlineAt(), form.getProjectId(),
                plannedDate, backlog, form.getPriority(), form.getStatus(),
                form.getEstimateMinutes(), form.getSpentMinutes()
        );
        taskApplicationService.editTask(command);
        redirectAttributes.addFlashAttribute("flashMessage", "Task updated.");
        String statusRedirect = statusListRedirect(form.getStatus());
        if (statusRedirect != null) return "redirect:" + statusRedirect;
        LocalDate redirectDate = plannedDate != null ? plannedDate : plannedForAt == null ? null : plannedForAt.toLocalDate();
        return "redirect:" + (redirectDate == null ? "/" : resolveDateRedirect(redirectDate));
    }

    @PostMapping("/tasks/{taskId}/done")
    public String markTaskAsDone(@PathVariable Long taskId,
                                 @RequestParam(value = "spentMinutes", required = false) Integer spentMinutes,
                                 @RequestParam(value = "redirectTo", required = false) String redirectTo,
                                 RedirectAttributes redirectAttributes) {
        taskApplicationService.markTaskAsDone(taskId, spentMinutes);
        redirectAttributes.addFlashAttribute("flashMessage", "Task marked as done.");
        return "redirect:" + resolveRedirectTarget(redirectTo, "/");
    }

    @PostMapping("/tasks/{taskId}/track-time")
    public String trackTime(@PathVariable Long taskId,
                            @RequestParam int minutes,
                            @RequestParam(value = "redirectTo", required = false) String redirectTo,
                            RedirectAttributes redirectAttributes) {
        taskApplicationService.trackTime(taskId, minutes);
        redirectAttributes.addFlashAttribute("flashMessage", "Time tracked.");
        return "redirect:" + resolveRedirectTarget(redirectTo, "/");
    }

    @PostMapping("/tasks/{taskId}/closed")
    public String markTaskAsClosed(@PathVariable Long taskId,
                                   @RequestParam(value = "redirectTo", required = false) String redirectTo,
                                   RedirectAttributes redirectAttributes) {
        taskApplicationService.markTaskAsClosed(taskId);
        redirectAttributes.addFlashAttribute("flashMessage", "Task closed.");
        return "redirect:" + resolveRedirectTarget(redirectTo, "/");
    }

    @PostMapping("/tasks/{taskId}/reschedule")
    public String rescheduleTask(@PathVariable Long taskId,
                                 @RequestParam("plannedForAt") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime plannedForAt,
                                 @RequestParam(value = "redirectTo", required = false) String redirectTo,
                                 RedirectAttributes redirectAttributes) {
        taskApplicationService.moveTask(new RescheduleTaskCommand(taskId, plannedForAt));
        redirectAttributes.addFlashAttribute("flashMessage", "Task moved to a new date.");
        return "redirect:" + resolveRedirectTarget(redirectTo, resolveDateRedirect(plannedForAt.toLocalDate()));
    }

    @PostMapping("/tasks/{taskId}/backlog")
    public String moveTaskToBacklog(@PathVariable Long taskId,
                                    @RequestParam(value = "redirectTo", required = false) String redirectTo,
                                    RedirectAttributes redirectAttributes) {
        taskApplicationService.moveTaskToBacklog(new MoveTaskToBacklogCommand(taskId));
        redirectAttributes.addFlashAttribute("flashMessage", "Task moved to backlog.");
        return "redirect:" + resolveRedirectTarget(redirectTo, "/backlog");
    }

    @PostMapping("/tasks/bulk-move-to-tomorrow")
    public String bulkMoveToTomorrow(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(value = "redirectTo", required = false) String redirectTo,
                                     RedirectAttributes redirectAttributes) {
        int movedCount = taskApplicationService.bulkMoveUnfinishedTasksToTomorrow(date);
        redirectAttributes.addFlashAttribute("flashMessage", "Moved " + movedCount + " unfinished tasks to tomorrow.");
        return "redirect:" + resolveRedirectTarget(redirectTo, resolveDateRedirect(date));
    }

    @PostMapping("/tasks/bulk-move-overdue-to-today")
    public String bulkMoveOverdueToToday(RedirectAttributes redirectAttributes) {
        int movedCount = taskApplicationService.bulkMoveOverdueTasksToToday();
        redirectAttributes.addFlashAttribute("flashMessage", "Moved " + movedCount + " overdue tasks to today.");
        return "redirect:/";
    }

    @PostMapping("/tasks/random-today")
    public String randomTaskForToday(RedirectAttributes redirectAttributes) {
        taskApplicationService.getRandomTaskForToday()
                .ifPresentOrElse(
                        task -> redirectAttributes.addFlashAttribute("randomTaskTitle", task.title()),
                        () -> redirectAttributes.addFlashAttribute("flashMessage", "No active tasks for today.")
                );
        return "redirect:/";
    }

    @PostMapping(\u0022/tasks/{taskId}/project\u0022)
    public String moveTaskToProject(@PathVariable Long taskId,@RequestParam Long projectId,@RequestParam(required=false) String redirectTo) {
        taskApplicationService.moveTaskToProject(taskId,projectId);
        return \u0022redirect:\u0022 + resolveRedirectTarget(redirectTo, \u0022/\u0022);
    }

    private static void populateDailyModel(Model model,
                                           Principal principal,
                                           DailyTasksView dailyTasksView,
                                           BacklogTasksView backlogTasksView,
                                           BacklogTasksView inboxTasksView,
                                           BacklogTasksView finishedTasksView,
                                           boolean todayPage) {
        model.addAttribute("appName", "DoneIt");
        model.addAttribute("username", principal.getName());
        model.addAttribute("daily", dailyTasksView);
        model.addAttribute("backlogPreview", backlogTasksView.tasks().stream().limit(3).toList());
        model.addAttribute("inboxPreview", inboxTasksView.tasks().stream().limit(3).toList());
        model.addAttribute("finishedPreview", finishedTasksView.tasks().stream().limit(3).toList());
        model.addAttribute("todayPage", todayPage);
    }

    private void populateTaskFormModel(Model model, Principal principal, TaskUpsertForm form) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("form", form);
        if (projectService != null) model.addAttribute(\u0022projects\u0022, projectService.list());
    }

    private static String resolveRedirectTarget(String redirectTo, String fallback) {
        if (redirectTo == null || redirectTo.isBlank() || !redirectTo.startsWith("/")) {
            return fallback;
        }
        return redirectTo;
    }

    private static String statusListRedirect(TaskStatus status) {
        if (status == TaskStatus.NEW) return "/inbox";
        if (status == TaskStatus.BACKLOG || status == TaskStatus.PAUSED) return "/backlog";
        if (status == TaskStatus.DONE || status == TaskStatus.CLOSED) return "/finished";
        return null;
    }

    private static boolean hasNoDates(TaskStatus status) {
        return status == TaskStatus.NEW || status == TaskStatus.BACKLOG || status == TaskStatus.PAUSED;
    }

    private String resolveEditRedirect(LocalDateTime plannedForAt) {
        if (plannedForAt == null) {
            return "/backlog";
        }
        return resolveDateRedirect(plannedForAt.toLocalDate());
    }

    private String resolveDateRedirect(LocalDate plannedDate) {
        LocalDate today = taskApplicationService.getTasksForToday().selectedDate();
        if (plannedDate.equals(today)) {
            return "/";
        }
        return "/tasks?date=" + plannedDate;
    }
}
