package com.doneit.analytics.web;

import com.doneit.analytics.application.WorkloadAnalyticsService;
import com.doneit.project.application.ProjectService;
import com.doneit.task.domain.PlanningType;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WorkloadAnalyticsController {
    private final WorkloadAnalyticsService analytics;
    private final ProjectService projects;
    private final Clock clock;
    public WorkloadAnalyticsController(WorkloadAnalyticsService analytics,ProjectService projects,Clock clock) {
        this.analytics=analytics;this.projects=projects;this.clock=clock;
    }
    @GetMapping("/analytics/workload")
    public String workload(@RequestParam(defaultValue="30") int days,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required=false) Long projectId,
            @RequestParam(required=false) PlanningType planningType,Model model) {
        LocalDate end=endDate==null?LocalDate.now(clock):endDate;
        model.addAttribute("report",analytics.report(end,days,projectId,planningType));
        model.addAttribute("projects",projects.list());
        model.addAttribute("selectedProjectId",projectId);
        model.addAttribute("selectedPlanningType",planningType);
        model.addAttribute("planningTypes",PlanningType.values());
        return "workload-analytics";
    }
}
