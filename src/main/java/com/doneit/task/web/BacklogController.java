package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.task.application.BacklogPageService;
import com.doneit.task.domain.PlanningType;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BacklogController {
    private final BacklogPageService backlog;
    private final ProjectService projects;
    private final Clock clock;

    public BacklogController(BacklogPageService backlog, ProjectService projects, Clock clock) {
        this.backlog = backlog;
        this.projects = projects;
        this.clock = clock;
    }

    @GetMapping("/backlog")
    public String backlog(@RequestParam(required = false) Long projectId,
                          @RequestParam(required = false) PlanningType planningType,
                          @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "1") long page, Model model) {
        var result = backlog.page(projectId, planningType, size, page);
        String returnTo = result.paging().href(result.paging().number());
        model.addAttribute("backlogRows", result.rows());
        model.addAttribute("pagination", result.paging());
        model.addAttribute("inboxToday", LocalDate.now(clock));
        model.addAttribute("projects", projects.list());
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("backlogReturnTo", returnTo);
        model.addAttribute("backlogCreateHref", "/tasks/new?backlog=true" + (projectId == null ? "" : "&projectId=" + projectId)
                + "&redirectTo=" + java.net.URLEncoder.encode(returnTo, java.nio.charset.StandardCharsets.UTF_8));
        model.addAttribute("compactReturnTo", returnTo);
        return "backlog";
    }
}
