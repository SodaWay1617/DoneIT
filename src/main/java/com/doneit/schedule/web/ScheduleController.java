package com.doneit.schedule.web;

import com.doneit.project.application.ProjectService;
import com.doneit.schedule.application.ScheduleService;
import com.doneit.task.domain.PlanningType;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ScheduleController {
    private final ScheduleService schedules;
    private final ProjectService projects;
    private final Clock clock;
    public ScheduleController(ScheduleService schedules,ProjectService projects,Clock clock) {
        this.schedules=schedules; this.projects=projects; this.clock=clock;
    }
    @GetMapping("/schedule")
    public String day(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(required=false) Long projectId,
                      @RequestParam(required=false) PlanningType planningType, Model model) {
        LocalDate selected=date==null ? LocalDate.now(clock) : date;
        model.addAttribute("schedule",schedules.day(selected,projectId,planningType));
        model.addAttribute("selectedDate",selected);
        model.addAttribute("today",LocalDate.now(clock));
        model.addAttribute("nowMinute",java.time.LocalTime.now(clock).getHour()*60+java.time.LocalTime.now(clock).getMinute());
        model.addAttribute("projects",projects.list());
        model.addAttribute("selectedProjectId",projectId);
        return "schedule";
    }
}
