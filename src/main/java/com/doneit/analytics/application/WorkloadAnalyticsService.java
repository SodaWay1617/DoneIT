package com.doneit.analytics.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.recurring.domain.RecurrenceType;
import com.doneit.task.domain.PlanningType;
import com.doneit.user.application.WorkloadSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class WorkloadAnalyticsService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final PlanFactService comparisons;
    private final WorkloadSettingsService thresholds;
    private final ObjectMapper json;

    public WorkloadAnalyticsService(JdbcTemplate db,ProjectService projects,PlanFactService comparisons,
            WorkloadSettingsService thresholds,ObjectMapper json) {
        this.db=db;this.projects=projects;this.comparisons=comparisons;this.thresholds=thresholds;this.json=json;
    }

    public WorkloadAnalyticsView report(LocalDate lastDay,int rangeDays,Long projectId,PlanningType category) {
        if(rangeDays<1||rangeDays>366) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST);
        if(projectId!=null&&!projects.canAccess(projectId)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN);
        LocalDate start=lastDay.minusDays(rangeDays-1),end=lastDay.plusDays(1);
        Long user=projects.user().id();
        LocalDate since=db.queryForObject("SELECT started_at::date FROM workload_history_origin",LocalDate.class);
        List<Fact> facts=new ArrayList<>();
        facts.addAll(facts(false,user,start,end,projectId,category));
        facts.addAll(facts(true,user,start,end,projectId,category));
        Map<LocalDate,Long> actualByDay=new HashMap<>();
        Map<Long,WorkloadAnalyticsView.Project> projectRows=new HashMap<>();
        for(Fact fact:facts) {
            actualByDay.merge(fact.date(),fact.minutes(),Long::sum);
            var previous=projectRows.get(fact.projectId());
            projectRows.put(fact.projectId(),new WorkloadAnalyticsView.Project(fact.projectId(),fact.projectName(),
                    fact.minutes()+(previous==null?0:previous.minutes())));
        }
        Map<PlanKey,Plan> plans=new HashMap<>();
        addPlans(plans,false,user,start,end,projectId,category);
        addPlans(plans,true,user,start,end,projectId,category);
        Map<LocalDate,long[]> plannedByDay=new HashMap<>();
        for(var entry:plans.entrySet()) {
            long[] values=plannedByDay.computeIfAbsent(entry.getKey().date(),ignored->new long[3]);
            values[entry.getValue().category().ordinal()]+=entry.getValue().minutes();
        }
        var settings=thresholds.current();
        List<WorkloadAnalyticsView.Day> days=new ArrayList<>();
        for(LocalDate day=start;day.isBefore(end);day=day.plusDays(1)) {
            long[] p=plannedByDay.getOrDefault(day,new long[3]);
            long actual=actualByDay.getOrDefault(day,0L);
            // Obligations determine planned overload; real tracked work always counts.
            long load=Math.max(p[PlanningType.REQUIRED.ordinal()],actual);
            days.add(new WorkloadAnalyticsView.Day(day,p[0],p[1],p[2],actual,!day.isBefore(since),
                    settings.level((int)Math.min(Integer.MAX_VALUE,Math.max(0,load))),load>settings.greenMinutes()));
        }
        long moved=countReschedules(user,start,end,projectId,category,false);
        long movedTasks=countReschedules(user,start,end,projectId,category,true);
        long cancelled=countCancelled(false,user,start,end,projectId,category)
                +countCancelled(true,user,start,end,projectId,category);
        return new WorkloadAnalyticsView(start,lastDay,rangeDays,since,
                days.stream().mapToLong(WorkloadAnalyticsView.Day::plannedMinutes).sum(),
                facts.stream().mapToLong(Fact::minutes).sum(),moved,movedTasks,cancelled,
                comparisons.accuracy(start,end,projectId,category),List.copyOf(days),
                projectRows.values().stream().sorted(Comparator.comparingLong(WorkloadAnalyticsView.Project::minutes)
                        .reversed().thenComparing(WorkloadAnalyticsView.Project::name)).toList(),settings,taskRows(plans,facts,user));
    }

    private List<Fact> facts(boolean regular,Long user,LocalDate start,LocalDate end,Long project,PlanningType category) {
        String table=regular?"regular_tasks":"tasks",entries=regular?"regular_task_time_entries":"task_time_entries";
        String column=regular?"regular_task_id":"task_id";
        String sql="SELECT e.entry_date,p.id,p.name,t.id,SUM(e.minutes) minutes FROM "+entries+" e JOIN "+table
                +" t ON t.id=e."+column+" JOIN projects p ON p.id=t.project_id WHERE e.user_id=?"
                +" AND e.entry_date>=? AND e.entry_date<? AND "+TaskVisibility.memberTasks("t",regular);
        List<Object> args=new ArrayList<>(List.of(user,start,end,user));
        sql=filter(sql,args,project,category,"t");
        return db.query(sql+" GROUP BY e.entry_date,p.id,p.name,t.id",(r,n)->new Fact(r.getObject(1,LocalDate.class),
                r.getLong(2),r.getString(3),r.getLong(5),new TaskKey(regular,r.getLong(4))),args.toArray());
    }

    private List<WorkloadAnalyticsView.Task> taskRows(Map<PlanKey,Plan> plans,List<Fact> facts,Long user) {
        Map<TaskKey,Long> planned=new HashMap<>(),actual=new HashMap<>();
        plans.forEach((key,value)->planned.merge(new TaskKey(key.regular(),key.id()),value.minutes(),Long::sum));
        facts.forEach(fact->actual.merge(fact.task(),fact.minutes(),Long::sum));
        List<WorkloadAnalyticsView.Task> rows=new ArrayList<>();
        for(boolean recurring:List.of(false,true)) {
            String table=recurring?"regular_tasks":"tasks";
            rows.addAll(db.query("SELECT t.id,t.title,p.name,t.status FROM "+table
                    +" t JOIN projects p ON p.id=t.project_id WHERE "+TaskVisibility.memberTasks("t",recurring),
                    (r,n)-> {
                        TaskKey key=new TaskKey(recurring,r.getLong(1));
                        if(!planned.containsKey(key)&&!actual.containsKey(key)) return null;
                        return new WorkloadAnalyticsView.Task(key.id(),recurring,r.getString(2),r.getString(3),
                                com.doneit.task.domain.TaskStatus.valueOf(r.getString(4)),planned.getOrDefault(key,0L),
                                actual.getOrDefault(key,0L),planned.containsKey(key),actual.containsKey(key));
                    },user).stream().filter(Objects::nonNull).toList());
        }
        return rows.stream().sorted(Comparator.comparingLong(WorkloadAnalyticsView.Task::actualMinutes).reversed()
                .thenComparing(WorkloadAnalyticsView.Task::title,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private void addPlans(Map<PlanKey,Plan> plans,boolean regular,Long user,LocalDate start,LocalDate end,
            Long project,PlanningType category) {
        String column=regular?"regular_task_id":"task_id",table=regular?"regular_tasks":"tasks";
        // LEAD must run before personal/category filtering, so reassignment ends the old version.
        String sql="WITH versions AS (SELECT h.*,LEAD(observed_at) OVER (PARTITION BY "+column
                +" ORDER BY observed_at,id) until_at FROM task_planning_history h WHERE "+column+" IS NOT NULL) "
                +"SELECT h."+column+",h.state::text,h.observed_at,h.until_at FROM versions h JOIN "+table
                +" t ON t.id=h."+column+" AND t.project_id=h.project_id WHERE ?=ANY(h.responsible_users)"
                +" AND h.observed_at<? AND (h.until_at IS NULL OR h.until_at>?) AND "
                +TaskVisibility.memberTasks("t",regular);
        List<Object> args=new ArrayList<>(List.of(user,end.atStartOfDay(),start.atStartOfDay(),user));
        if(project!=null) {sql+=" AND h.project_id=?";args.add(project);}
        if(category!=null) {sql+=" AND h.state->>'planning_type'=?";args.add(category.name());}
        db.query(sql+" ORDER BY h.observed_at,h.id",r->{
            JsonNode state;
            try {state=json.readTree(r.getString(2));} catch(java.io.IOException e) {throw new IllegalStateException(e);}
            LocalDateTime observed=r.getObject(3,LocalDateTime.class),until=r.getObject(4,LocalDateTime.class);
            if(until!=null&&!until.isAfter(observed)) return;
            String status=text(state,"status");
            if(!Set.of("SPECIFICATION","TODO","IN_PROGRESS","DOCUMENTATION").contains(status)) return;
            LocalDate first=observed.toLocalDate().isAfter(start)?observed.toLocalDate():start;
            for(LocalDate day=first;day.isBefore(end);day=day.plusDays(1)) {
                if(until!=null&&!day.atStartOfDay().isBefore(until)) break;
                boolean occurs;
                if(regular) {
                    LocalDate activated=date(state,"activated_at"),finished=date(state,"finished_at");
                    occurs=activated!=null&&!day.isBefore(activated)&&(finished==null||!day.isAfter(finished))
                            &&RecurrenceType.valueOf(text(state,"recurrence_type")).occursOn(day,
                                date(state,"anchor_date"),state.path("repeat_interval").asInt(1));
                } else {
                    LocalDate planned=date(state,"planned_date");
                    if(planned==null) planned=date(state,"planned_for_at");
                    // Undated ordinary work has no promised day and is not a historical daily plan.
                    occurs=day.equals(planned);
                }
                if(occurs) plans.put(new PlanKey(regular,r.getLong(1),day),new Plan(
                        PlanningType.valueOf(text(state,"planning_type")),state.path("estimate_minutes").asLong(0)));
            }
        },args.toArray());
    }

    private long countReschedules(Long user,LocalDate start,LocalDate end,Long project,PlanningType category,boolean unique) {
        String sql="SELECT "+(unique?"COUNT(DISTINCT h.task_id)":"COUNT(*)")
                +" FROM task_reschedules h JOIN tasks t ON t.id=h.task_id AND t.project_id=h.project_id"
                +" WHERE ?=ANY(h.responsible_users) AND h.changed_at>=? AND h.changed_at<? AND "
                +TaskVisibility.memberTasks("t",false);
        List<Object> args=new ArrayList<>(List.of(user,start.atStartOfDay(),end.atStartOfDay(),user));
        sql=filter(sql,args,project,category,"h");
        return db.queryForObject(sql,Long.class,args.toArray());
    }

    private long countCancelled(boolean regular,Long user,LocalDate start,LocalDate end,Long project,PlanningType category) {
        String table=regular?"regular_tasks":"tasks",closed=regular?"finished_at":"closed_at";
        String column=regular?"regular_task_id":"task_id";
        String sql="SELECT COUNT(DISTINCT h."+column+") FROM task_planning_history h JOIN "+table
                +" t ON t.id=h."+column+" AND t.project_id=h.project_id WHERE h.state->>'status'='CLOSED'"
                +" AND CAST(h.state->>'"+closed+"' AS timestamp)>=? AND CAST(h.state->>'"+closed+"' AS timestamp)<?"
                +" AND ?=ANY(h.responsible_users) AND "+TaskVisibility.memberTasks("t",regular);
        List<Object> args=new ArrayList<>(List.of(start.atStartOfDay(),end.atStartOfDay(),user,user));
        if(project!=null) {sql+=" AND h.project_id=?";args.add(project);}
        if(category!=null) {sql+=" AND h.state->>'planning_type'=?";args.add(category.name());}
        return db.queryForObject(sql,Long.class,args.toArray());
    }
    private static String filter(String sql,List<Object> args,Long project,PlanningType category,String alias) {
        if(project!=null) {sql+=" AND "+alias+".project_id=?";args.add(project);}
        if(category!=null) {sql+=" AND "+alias+".planning_type=?";args.add(category.name());}
        return sql;
    }
    private static String text(JsonNode state,String field) {return state.path(field).asText();}
    private static LocalDate date(JsonNode state,String field) {
        JsonNode value=state.path(field);
        if(value.isMissingNode()||value.isNull()) return null;
        return field.endsWith("_at")
                ?LocalDateTime.parse(value.asText()).toLocalDate():LocalDate.parse(value.asText());
    }
    private record Fact(LocalDate date,Long projectId,String projectName,long minutes,TaskKey task) {}
    private record TaskKey(boolean regular,long id) {}
    private record PlanKey(boolean regular,long id,LocalDate date) {}
    private record Plan(PlanningType category,long minutes) {}
}
