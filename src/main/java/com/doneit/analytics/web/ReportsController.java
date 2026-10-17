package com.doneit.analytics.web;

import com.doneit.analytics.application.ReportType;
import com.doneit.analytics.application.ReportsService;
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
public class ReportsController {
    private final ReportsService reports;
    private final ProjectService projects;
    private final Clock clock;
    public ReportsController(ReportsService reports,ProjectService projects,Clock clock) {
        this.reports=reports;this.projects=projects;this.clock=clock;
    }
    @GetMapping("/reports")
    public String report(@RequestParam(defaultValue="MONTH") ReportType type,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) Long projectId,@RequestParam(required=false) PlanningType planningType,Model model) {
        LocalDate selected=date==null?LocalDate.now(clock):date;
        var accessible=projects.list();
        if(type==ReportType.PROJECT) {
            if(projectId==null) projectId=accessible.stream().findFirst().orElseThrow().id();
            var project=reports.project(projectId,planningType);
            model.addAttribute("projectReport",project);
            model.addAttribute("quality",project.accuracy());
        } else {
            var report=reports.period(type.period(),selected,projectId,planningType);
            model.addAttribute("periodReport",report);
            model.addAttribute("quality",report.workload().accuracy());
        }
        model.addAttribute("type",type);
        model.addAttribute("types",ReportType.values());
        model.addAttribute("selectedDate",selected);
        model.addAttribute("projects",accessible);
        model.addAttribute("selectedProjectId",projectId);
        model.addAttribute("selectedPlanningType",planningType);
        model.addAttribute("planningTypes",PlanningType.values());
        model.addAttribute("login",projects.user().login());
        return "reports";
    }
}
