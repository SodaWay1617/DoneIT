package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.task.application.TaskApplicationService;
import com.doneit.task.application.command.CreateTaskCommand;
import com.doneit.task.application.command.EditTaskCommand;
import com.doneit.task.application.command.MoveTaskToBacklogCommand;
import com.doneit.task.application.command.RescheduleTaskCommand;
import com.doneit.task.application.view.BacklogTasksView;
import com.doneit.task.application.view.DailyTasksView;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Controller
public class HomeController {

    private final TaskApplicationService taskApplicationService;
    private final ProjectService projectService;

    public HomeController(TaskApplicationService taskApplicationService) {
        this(taskApplicationService, null);
    }

    @Autowired
    public HomeController(TaskApplicationService taskApplicationService, ProjectService projectService) {
        this.taskApplicationService = taskApplicationService;
        this.projectService = projectService;
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
        populateDailyModel(model, principal, dailyTasksView, backlogTasksView, true);
        return "tasks";
    }

    @GetMapping("/tasks")
    public String tasksForDate(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required=false) Long projectId,
                               Model model,
                               Principal principal) {
        DailyTasksView dailyTasksView = taskApplicationService.getTasksForDate(date,projectId);
        BacklogTasksView backlogTasksView = taskApplicationService.getBacklogTasks(projectId);
        populateDailyModel(model, principal, dailyTasksView, backlogTasksView, false);
        return "tasks";
    }

    @GetMapping("/backlog")
    public String backlog(@RequestParam(required=false) Long projectId,Model model, Principal principal) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("backlog", taskApplicationService.getBacklogTasks(projectId));
        return "backlog";
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

    @GetMapping("/tasks/new")
    public String createTaskPage(Model model, Principal principal) {
        populateTaskFormModel(model, principal, TaskUpsertForm.from(taskApplicationService.getCreateTaskForm()));
        model.addAttribute("pageTitle", "Create Task");
        model.addAttribute("submitLabel", "Create task");
        model.addAttribute("formAction", "/tasks");
        return "task-form";
    }

    @PostMapping("/tasks")
    public String createTask(@Valid @ModelAttribute("form") TaskUpsertForm form,
                             BindingResult bindingResult,
                             Model model,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateTaskFormModel(model, principal, form);
            model.addAttribute("pageTitle", "Create Task");
            model.addAttribute("submitLabel", "Create task");
            model.addAttribute("formAction", "/tasks");
            return "task-form";
        }

        CreateTaskCommand command = new CreateTaskCommand(
                form.getTitle(),
                form.getDescription(),
                form.isBacklog() ? null : form.getPlannedForAt(),
                form.getDeadlineAt(), form.getProjectId(),
                form.isBacklog() ? null : form.getPlannedDate(), form.isBacklog()
        );

        if (form.isBacklog()) {
            taskApplicationService.createBacklogTask(command);
            redirectAttributes.addFlashAttribute("flashMessage", "Task added to backlog.");
            return "redirect:/backlog";
        }

        taskApplicationService.createTask(command);
        redirectAttributes.addFlashAttribute("flashMessage", "Task created.");
        LocalDate plannedDate = form.getPlannedDate() != null ? form.getPlannedDate() : form.getPlannedForAt() == null ? null : form.getPlannedForAt().toLocalDate();
        return "redirect:" + (plannedDate == null ? "/" : resolveDateRedirect(plannedDate));
    }

    @GetMapping("/tasks/{taskId}/edit")
    public String editTaskPage(@PathVariable Long taskId, Model model, Principal principal) {
        populateTaskFormModel(model, principal, TaskUpsertForm.from(taskApplicationService.getTaskForEdit(taskId)));
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

        EditTaskCommand command = new EditTaskCommand(
                taskId,
                form.getTitle(),
                form.getDescription(),
                form.isBacklog() ? null : form.getPlannedForAt(),
                form.getDeadlineAt(), form.getProjectId(),
                form.isBacklog() ? null : form.getPlannedDate(), form.isBacklog()
        );
        taskApplicationService.editTask(command);
        redirectAttributes.addFlashAttribute("flashMessage", "Task updated.");
        if (form.isBacklog()) return "redirect:/backlog";
        LocalDate plannedDate = form.getPlannedDate() != null ? form.getPlannedDate() : form.getPlannedForAt() == null ? null : form.getPlannedForAt().toLocalDate();
        return "redirect:" + (plannedDate == null ? "/" : resolveDateRedirect(plannedDate));
    }

    @PostMapping("/tasks/{taskId}/done")
    public String markTaskAsDone(@PathVariable Long taskId,
                                 @RequestParam(value = "redirectTo", required = false) String redirectTo,
                                 RedirectAttributes redirectAttributes) {
        taskApplicationService.markTaskAsDone(taskId);
        redirectAttributes.addFlashAttribute("flashMessage", "Task marked as done.");
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
                                           boolean todayPage) {
        model.addAttribute("appName", "DoneIt");
        model.addAttribute("username", principal.getName());
        model.addAttribute("daily", dailyTasksView);
        model.addAttribute("backlogPreview", backlogTasksView.tasks());
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
