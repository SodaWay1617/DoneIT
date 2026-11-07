package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.recurring.application.RegularTaskService;
import com.doneit.task.application.view.PlanningLoad;
import com.doneit.task.domain.*;
import com.doneit.user.application.WipSettingsService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WipService {
    public record Plan(LocalDate date, TaskStatus status, Integer minutes, PlanningType category) {
        public long contribution() { return date!=null && PlanningLoad.active(status) && minutes!=null ? minutes : 0; }
        public static Plan of(Task task) {
            return new Plan(task.plannedForAt()!=null?task.plannedForAt().toLocalDate():task.plannedDate(),
                    task.status(),task.estimateMinutes(),task.planningType());
        }
    }
    public record Change(Plan before, Plan after) {}
    public record Warning(LocalDate date, long before, long after, long limit, long excess, long added) {}
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final RegularTaskService regular;
    private final WipSettingsService settings;
    private final WipConsent consent;
    public WipService(JdbcTemplate db,ProjectService projects,RegularTaskService regular,WipSettingsService settings,WipConsent consent) {
        this.db=db;this.projects=projects;this.regular=regular;this.settings=settings;this.consent=consent;
    }
    public void check(Task before, Task after) {
        if(!consent.interactive())return;
        if(before!=null && !projects.personalTaskIds(false,List.of(before.id())).contains(before.id()))return;
        Plan next=Plan.of(after);
        check(List.of(new Change(before==null?null:Plan.of(before),
                new Plan(next.date(),next.status(),next.minutes(),consent.category(next.category())))));
    }
    public void move(Task task,LocalDate date) {
        if(!consent.interactive() || !projects.personalTaskIds(false,List.of(task.id())).contains(task.id()))return;
        var status=task.status()==TaskStatus.NEW || task.status()==TaskStatus.BACKLOG?TaskStatus.TODO:task.status();
        check(List.of(new Change(Plan.of(task),new Plan(date,status,task.estimateMinutes(),task.planningType()))));
    }
    public void check(List<Change> changes) {
        if(!consent.interactive())return;
        var config=settings.current();
        if(!config.enabled())return;
        Set<LocalDate> dates=new TreeSet<>();
        for(var c:changes)if(candidate(c))dates.add(c.after().date());
        if(dates.isEmpty())return;
        Map<LocalDate,Long> loads=loads(dates);
        var warnings=evaluate(changes,loads,config.minutes());
        if(!warnings.isEmpty())consent.confirm(warnings);
    }
    public static List<Warning> evaluate(List<Change> changes,Map<LocalDate,Long> loads,long limit) {
        Map<LocalDate,Long> delta=new TreeMap<>();Set<LocalDate> candidates=new HashSet<>();
        for(Change change:changes) {
            Plan before=change.before(),after=change.after();
            if(before!=null && before.date()!=null)delta.merge(before.date(),-before.contribution(),Long::sum);
            if(after.date()!=null)delta.merge(after.date(),after.contribution(),Long::sum);
            if(candidate(change))candidates.add(after.date());
        }
        List<Warning> warnings=new ArrayList<>();
        for(LocalDate day:candidates.stream().sorted().toList()) {
            long before=loads.getOrDefault(day,0L),added=delta.getOrDefault(day,0L),after=before+added;
            boolean categoryChange=changes.stream().anyMatch(c->c.before()!=null && Objects.equals(day,c.before().date())
                    && Objects.equals(day,c.after().date()) && c.before().category()==PlanningType.REQUIRED
                    && c.after().category()!=PlanningType.REQUIRED && c.after().contribution()>=c.before().contribution());
            if(after>limit && (added>0 || added==0 && categoryChange))
                warnings.add(new Warning(day,before,after,limit,after-limit,Math.max(0,added)));
        }
        return List.copyOf(warnings);
    }
    private static boolean candidate(Change change) {
        Plan before=change.before(),after=change.after();
        long previous=before!=null && Objects.equals(before.date(),after.date())?before.contribution():0;
        boolean releasedRequired=before!=null && before.category()==PlanningType.REQUIRED && after.category()!=PlanningType.REQUIRED;
        return after.date()!=null && after.contribution()>0 && after.category()!=PlanningType.REQUIRED
                && (after.contribution()>previous || releasedRequired && after.contribution()==previous);
    }
    private Map<LocalDate,Long> loads(Set<LocalDate> dates) {
        var args=new ArrayList<Object>();args.add(projects.user().id());args.addAll(dates);
        Map<LocalDate,Long> result=new HashMap<>();
        db.query("SELECT COALESCE(t.planned_for_at::date,t.planned_date) AS load_date,SUM(COALESCE(t.estimate_minutes,0)) AS minutes FROM tasks t WHERE "
                +TaskVisibility.memberTasks("t",false,true)
                +" AND t.status IN ('SPECIFICATION','TODO','IN_PROGRESS','DOCUMENTATION') AND COALESCE(t.planned_for_at::date,t.planned_date) IN ("
                +String.join(",",Collections.nCopies(dates.size(),"?"))+") GROUP BY 1",
                rs->{result.put(rs.getObject("load_date",LocalDate.class),rs.getLong("minutes"));},args.toArray());
        var occurrences=regular.occurrences(dates.iterator().next(),Collections.max(dates).plusDays(1),null);
        var personal=projects.personalTaskIds(true,occurrences.stream().map(o->o.task().id()).distinct().toList());
        for(var o:occurrences)if(dates.contains(o.date()) && personal.contains(o.task().id()) && !o.completed()
                && PlanningLoad.active(o.task().status()))
            result.merge(o.date(),o.task().estimateMinutes()==null?0L:o.task().estimateMinutes().longValue(),Long::sum);
        return result;
    }
    public void bulk(LocalDate beforeDay,boolean shift) {
        if(!consent.interactive() || !settings.current().enabled())return;
        var changes=db.query("SELECT COALESCE(t.planned_for_at::date,t.planned_date) AS load_date,t.status,t.estimate_minutes,t.planning_type FROM tasks t WHERE "
                +TaskVisibility.memberTasks("t",false,true)
                +" AND t.planning_type<>'OPTIONAL' AND t.status IN ('SPECIFICATION','TODO','IN_PROGRESS','DOCUMENTATION')"
                +" AND ((t.planned_for_at IS NOT NULL AND t.planned_for_at<?) OR t.planned_date<?)",
                (rs,row)->{
                    LocalDate day=rs.getObject("load_date",LocalDate.class);
                    var status=TaskStatus.valueOf(rs.getString("status"));
                    Integer minutes=rs.getObject("estimate_minutes",Integer.class);
                    var category=PlanningType.valueOf(rs.getString("planning_type"));
                    return new Change(new Plan(day,status,minutes,category),new Plan(shift?day.plusDays(1):beforeDay,status,minutes,category));
                },projects.user().id(),beforeDay.atStartOfDay(),beforeDay);
        check(changes);
    }
}
