package com.doneit.analytics.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.task.domain.PlanningType;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ReportsService {
    private final WorkloadAnalyticsService workload;
    private final PlanFactService comparisons;
    private final ProjectService projects;
    private final JdbcTemplate db;
    public ReportsService(WorkloadAnalyticsService workload,PlanFactService comparisons,ProjectService projects,JdbcTemplate db) {
        this.workload=workload;this.comparisons=comparisons;this.projects=projects;this.db=db;
    }
    public PeriodReport period(AnalyticsPeriod period,LocalDate date,Long project,PlanningType category) {
        LocalDate start=period.start(date),end=period.next(start);
        return new PeriodReport(workload.report(end.minusDays(1),(int)ChronoUnit.DAYS.between(start,end),project,category),
                comparisons.report(period,date,project,category));
    }
    public ProjectReport project(Long project,PlanningType category) {
        if(project==null||!projects.canAccess(project)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN);
        List<ProjectTask> ordinary=projectTasks(project,category,false),regular=projectTasks(project,category,true);
        return new ProjectReport(project,projects.get(project).name(),
                ordinary.stream().filter(t->t.responsible()&&t.status()!=TaskStatus.CLOSED&&t.estimateMinutes()!=null)
                        .mapToLong(ProjectTask::estimateMinutes).sum(),
                ordinary.stream().mapToLong(ProjectTask::actualMinutes).sum(),
                ordinary.stream().filter(t->t.responsible()&&t.status()==TaskStatus.DONE).count(),
                ordinary.stream().filter(t->t.responsible()&&!t.status().isFinished()).count(),
                ordinary.stream().filter(t->t.responsible()&&t.status()==TaskStatus.CLOSED).count(),
                regular.stream().mapToLong(ProjectTask::actualMinutes).sum(),
                regular.stream().filter(ProjectTask::responsible).mapToLong(ProjectTask::completedOccurrences).sum(),
                comparisons.accuracy(null,null,project,category),ordinary,regular);
    }
    private List<ProjectTask> projectTasks(Long project,PlanningType category,boolean recurring) {
        String table=recurring?"regular_tasks":"tasks",entries=recurring?"regular_task_time_entries":"task_time_entries";
        String column=recurring?"regular_task_id":"task_id";
        Long user=projects.user().id();
        String sql="SELECT t.id,t.title,t.status,t.estimate_minutes,"+TaskVisibility.responsible("t","pm.user_id",recurring)
                +" responsible,COALESCE((SELECT SUM(e.minutes) FROM "+entries+" e WHERE e."+column
                +"=t.id AND e.user_id=pm.user_id),0) actual,EXISTS(SELECT 1 FROM "+entries+" e WHERE e."
                +column+"=t.id AND e.user_id=pm.user_id) tracked,"
                +(recurring?"(SELECT COUNT(*) FROM regular_task_completions c WHERE c.regular_task_id=t.id)":"0")
                +" completed_occurrences FROM "+table+" t JOIN project_members pm ON pm.project_id=t.project_id"
                +" WHERE pm.user_id=? AND t.project_id=? AND "+TaskVisibility.visibleTo("t","pm.user_id",recurring);
        List<Object> args=new ArrayList<>(List.of(user,project));
        if(category!=null) {sql+=" AND t.planning_type=?";args.add(category.name());}
        return db.query(sql,(r,n)-> {
            if(!r.getBoolean("responsible")&&!r.getBoolean("tracked")) return null;
            Integer estimate=r.getBoolean("responsible")?(Integer)r.getObject("estimate_minutes"):null;
            return new ProjectTask(r.getLong("id"),recurring,r.getString("title"),TaskStatus.valueOf(r.getString("status")),
                    estimate==null?null:estimate.longValue(),r.getLong("actual"),r.getBoolean("responsible"),
                    r.getLong("completed_occurrences"));
        },args.toArray()).stream().filter(Objects::nonNull).sorted(Comparator.comparingLong(ProjectTask::actualMinutes).reversed()
                .thenComparing(ProjectTask::title,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public record PeriodReport(WorkloadAnalyticsView workload,PlanFactView completed) {}
    public record ProjectReport(Long id,String name,long estimateMinutes,long actualMinutes,long completed,long open,long closed,
            long regularActualMinutes,long regularCompleted,PlanFactService.Accuracy accuracy,
            List<ProjectTask> tasks,List<ProjectTask> recurringTasks) {
        public long unestimatedTasks() {
            return tasks.stream().filter(t->t.responsible()&&t.status()!=TaskStatus.CLOSED&&t.estimateMinutes()==null).count();
        }
    }
    public record ProjectTask(Long id,boolean recurring,String title,TaskStatus status,Long estimateMinutes,long actualMinutes,
            boolean responsible,long completedOccurrences) {
        public String href() { return (recurring?"/recurring/":"/tasks/")+id+"/edit"; }
    }
}
