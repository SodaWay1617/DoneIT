package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.task.application.InboxPageService;
import com.doneit.task.application.TaskApplicationService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class InboxController {
    private final InboxPageService inbox;
    private final TaskApplicationService tasks;
    private final ProjectService projects;
    private final Clock clock;

    public InboxController(InboxPageService inbox, TaskApplicationService tasks, ProjectService projects, Clock clock) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.projects = projects;
        this.clock = clock;
    }

    @GetMapping("/inbox")
    public String inbox(@RequestParam(required = false) Long projectId, Model model) {
        model.addAttribute("inboxRows", inbox.list(projectId));
        model.addAttribute("inboxToday", LocalDate.now(clock));
        model.addAttribute("projects", projects.list());
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("inboxReturnTo", "/inbox" + (projectId == null ? "" : "?projectId=" + projectId));
        return "inbox";
    }

    @PostMapping("/tasks/{id}/start-today")
    public String start(@PathVariable Long id, @RequestParam(defaultValue = "/inbox") String redirectTo) {
        tasks.startTaskToday(id);
        return "redirect:" + (redirectTo.startsWith("/") && !redirectTo.startsWith("//") ? redirectTo : "/inbox");
    }
}
