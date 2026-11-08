package com.doneit.task.web;

import com.doneit.project.application.ProjectService;
import com.doneit.recurring.application.RegularTaskService;
import com.doneit.task.application.*;
import java.security.Principal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class PlanningController {
    private final TaskApplicationService tasks;
    private final ProjectService projects;
    private final PlanningBoardService board;
    private final RegularTaskService regular;
    private final HomeController editor;
    public PlanningController(TaskApplicationService tasks,ProjectService projects,PlanningBoardService board,RegularTaskService regular,HomeController editor) {
        this.tasks=tasks;this.projects=projects;this.board=board;this.regular=regular;this.editor=editor;
    }
    @GetMapping("/planning")
    public String planning(@RequestParam(defaultValue="prepare") String mode,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) Long projectId,@RequestParam(required=false) Long taskId,
            @RequestParam(defaultValue="1") long backlogPage,@RequestParam(defaultValue="1") long inboxPage,Model model,Principal principal) {
        if(!java.util.Set.of("prepare","week").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(projectId!=null && !projects.canAccess(projectId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        LocalDate today=tasks.today(),start=(week==null?(date==null?today:date):week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate selected=date!=null && !date.isBefore(start) && date.isBefore(start.plusDays(7))?date:
                !today.isBefore(start) && today.isBefore(start.plusDays(7))?today:start;
        String back=url(mode,start,selected,projectId,backlogPage,inboxPage,null);
        model.addAttribute("plannerMode",mode);model.addAttribute("plannerDate",selected);model.addAttribute("plannerStart",start);
        model.addAttribute("plannerReturnTo",back);model.addAttribute("selectedProjectId",projectId);model.addAttribute("projects",projects.list());
        var backlogQueue=board.queue(true,projectId,backlogPage,back);
        backlogPage=backlogQueue.page();model.addAttribute("plannerBacklog",backlogQueue);
        model.addAttribute("plannerExplicitTask",taskId!=null);
        model.addAttribute("plannerEmpty",false);
        if("prepare".equals(mode)) {
            var inboxQueue=board.queue(false,projectId,inboxPage,back);
            inboxPage=inboxQueue.page();model.addAttribute("plannerInbox",inboxQueue);
            model.addAttribute("plannerEmpty",taskId==null && backlogQueue.total()==0 && inboxQueue.total()==0);
            if(taskId==null) {
                var available=backlogQueue.cards().isEmpty()?inboxQueue.cards():backlogQueue.cards();
                if(!available.isEmpty())taskId=available.getFirst().id();
            }
        }
        model.addAttribute("plannerBacklogPrevious",url(mode,start,selected,projectId,Math.max(1,backlogPage-1),inboxPage,null));
        model.addAttribute("plannerBacklogNext",url(mode,start,selected,projectId,backlogPage+1,inboxPage,null));
        model.addAttribute("plannerInboxPrevious",url(mode,start,selected,projectId,backlogPage,Math.max(1,inboxPage-1),null));
        model.addAttribute("plannerInboxNext",url(mode,start,selected,projectId,backlogPage,inboxPage+1,null));
        if(taskId!=null && "prepare".equals(mode)) {
            editor.editTaskPage(taskId,model,principal);model.addAttribute("planningReturnTo",back);model.addAttribute("plannerSelectedTask",taskId);
            model.addAttribute("planningIncoming",((TaskUpsertForm)model.getAttribute("form")).getStatus()==com.doneit.task.domain.TaskStatus.NEW);
        }
        model.addAttribute("plannerPrepareHref",url("prepare",start,selected,projectId,backlogPage,inboxPage,null));
        model.addAttribute("plannerWeekHref",url("week",start,selected,projectId,backlogPage,inboxPage,null));
        model.addAttribute("plannerPreviousHref",url(mode,start.minusWeeks(1),null,projectId,backlogPage,inboxPage,null));
        model.addAttribute("plannerNextHref",url(mode,start.plusWeeks(1),null,projectId,backlogPage,inboxPage,null));
        model.addAttribute("plannerTodayHref",url(mode,today,today,projectId,backlogPage,inboxPage,null));
        model.addAttribute("calendar",tasks.getCalendarWeek(selected,projectId));
        model.addAttribute("regularCalendar",regular.occurrences(start,start.plusDays(7),projectId));
        model.addAttribute("calendarMode","week");model.addAttribute("calendarWeek",start);
        model.addAttribute("calendarAnchor",selected);model.addAttribute("calendarFocus",selected);
        return "planner";
    }
    public static String url(String mode,LocalDate week,LocalDate date,Long project,long backlog,long inbox,Long task) {
        var url=UriComponentsBuilder.fromPath("/planning").queryParam("mode",mode).queryParam("week",week);
        if(date!=null)url.queryParam("date",date);
        if(project!=null)url.queryParam("projectId",project);
        if(backlog>1)url.queryParam("backlogPage",backlog);
        if(inbox>1)url.queryParam("inboxPage",inbox);
        if(task!=null)url.queryParam("taskId",task);
        return url.build().toUriString();
    }
    @PostMapping("/planning/tasks/{id}/schedule")
    public String schedule(@PathVariable Long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate plannedDate,
            @RequestParam(defaultValue="false") boolean plannedWithTime,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.TIME) LocalTime time,@RequestParam String redirectTo) {
        if(plannedWithTime && time==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A start time is required");
        String back=back(redirectTo);board.schedule(id,plannedDate,plannedWithTime?time:null);return "redirect:"+back;
    }
    @PostMapping("/planning/tasks/{id}/backlog")
    public String backlog(@PathVariable Long id,@RequestParam String redirectTo) {
        String back=back(redirectTo);board.backlog(id);return "redirect:"+back;
    }
    private static String back(String value) {
        if(value==null || value.length()>4000 || !value.matches("/planning(?:\\?[A-Za-z0-9%=&+._-]*)?"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid planning return address");
        return value;
    }
}
