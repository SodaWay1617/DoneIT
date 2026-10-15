package com.doneit.analytics.web;

import com.doneit.analytics.application.AnalyticsPeriod;
import com.doneit.analytics.application.PlanFactService;
import com.doneit.project.application.ProjectService;
import com.doneit.task.domain.PlanningType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AnalyticsController {
    private final PlanFactService analytics;
    private final ProjectService projects;
    private final Clock clock;
    public AnalyticsController(PlanFactService analytics,ProjectService projects,Clock clock) {
        this.analytics=analytics; this.projects=projects; this.clock=clock;
    }
    @GetMapping("/analytics")
    public String analytics(@RequestParam(required=false) String month,
                            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(defaultValue="MONTH") AnalyticsPeriod period,
                            @RequestParam(required=false) Long projectId,
                            @RequestParam(required=false) PlanningType planningType,Model model) {
        LocalDate selected=date==null?legacyMonth(month):date;
        model.addAttribute("selectedDate",selected);
        model.addAttribute("report",analytics.report(period,selected,projectId,planningType));
        model.addAttribute("projects",projects.list());
        model.addAttribute("selectedProjectId",projectId);
        model.addAttribute("selectedPlanningType",planningType);
        model.addAttribute("planningTypes",PlanningType.values());
        return "analytics";
    }
    private LocalDate legacyMonth(String value) {
        if(value!=null&&!value.isBlank()) {
            try { return YearMonth.parse(value).atDay(1); }
            catch(DateTimeParseException ignored) {}
        }
        return LocalDate.now(clock);
    }
}
