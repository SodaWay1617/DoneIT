package com.doneit.analytics.application;

import com.doneit.analytics.application.PlanFactView.*;
import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.task.domain.PlanningType;
import com.doneit.user.application.AnalyticsSettingsService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PlanFactService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final AnalyticsSettingsService settings;

    public PlanFactService(JdbcTemplate db,ProjectService projects,AnalyticsSettingsService settings) {
        this.db=db; this.projects=projects; this.settings=settings;
    }

    public PlanFactView report(AnalyticsPeriod period,LocalDate date,Long projectId,PlanningType category) {
        if(projectId!=null&&!projects.canAccess(projectId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        LocalDate start=period.start(date),end=period.next(start);
        List<Sample> samples=new ArrayList<>(ordinary(start,end,projectId,category,null));
        samples.addAll(regular(start,end,projectId,category,null));
        boolean roll=settings.rollUpSubtaskTime();
        Map<Key,TaskRow> tasks=new LinkedHashMap<>();
        Map<Long,ProjectRow> projectRows=new LinkedHashMap<>();
        Map<LocalDate,TimeComparison> days=new TreeMap<>();
        TimeComparison total=TimeComparison.empty();
        for(Sample sample:samples) {
            total=total.add(sample.comparison());
            boolean parent=roll&&!sample.recurring()&&sample.parentId()!=null;
            Long taskId=parent?sample.parentId():sample.id();
            String title=parent?sample.parentTitle():sample.title();
            Key key=new Key(taskId,sample.recurring());
            TaskRow row=tasks.get(key);
            tasks.put(key,new TaskRow(taskId,sample.recurring(),title,sample.projectName(),
                    (row==null?TimeComparison.empty():row.comparison()).add(sample.comparison())));
            ProjectRow project=projectRows.get(sample.projectId());
            projectRows.put(sample.projectId(),new ProjectRow(sample.projectId(),sample.projectName(),
                    (project==null?TimeComparison.empty():project.comparison()).add(sample.comparison())));
            days.merge(sample.date(),sample.comparison(),TimeComparison::add);
        }
        List<DayRow> dayRows=new ArrayList<>();
        for(LocalDate day=start;day.isBefore(end);day=day.plusDays(1))
            dayRows.add(new DayRow(day,days.getOrDefault(day,TimeComparison.empty())));
        List<TaskRow> taskRows=tasks.values().stream().sorted(Comparator
                .comparingLong((TaskRow t)->t.comparison().actualMinutes()).reversed()
                .thenComparing(TaskRow::title,String.CASE_INSENSITIVE_ORDER)).toList();
        List<ProjectRow> sortedProjects=projectRows.values().stream().sorted(Comparator
                .comparingLong((ProjectRow p)->p.comparison().actualMinutes()).reversed()
                .thenComparing(ProjectRow::name,String.CASE_INSENSITIVE_ORDER)).toList();
        return new PlanFactView(period,start,end,projects.user().login(),total,taskRows,sortedProjects,List.copyOf(dayRows),roll);
    }

    public TaskComparison task(Long id) {
        projects.requireTaskAccess(id,false);
        TimeComparison own=TimeComparison.empty(),children=TimeComparison.empty();
        for(Sample sample:ordinary(null,null,null,null,id)) {
            if(sample.id().equals(id)) own=own.add(sample.comparison());
            else children=children.add(sample.comparison());
        }
        return new TaskComparison(own,children);
    }

    public TaskComparison regularTask(Long id) {
        projects.requireTaskAccess(id,true);
        TimeComparison own=regular(null,null,null,null,id).stream().map(Sample::comparison)
                .reduce(TimeComparison.empty(),TimeComparison::add);
        return new TaskComparison(own,TimeComparison.empty());
    }

    private List<Sample> ordinary(LocalDate start,LocalDate end,Long projectId,PlanningType category,Long taskId) {
        String sql="""
                SELECT t.id,t.title,p.id project_id,p.name project_name,parent.id parent_id,parent.title parent_title,
                       CAST(t.completed_at AS date) completed_date,t.estimate_minutes,%s responsible,
                       COALESCE((SELECT SUM(e.minutes) FROM task_time_entries e
                                 WHERE e.task_id=t.id AND e.user_id=pm.user_id),0) actual,
                       EXISTS(SELECT 1 FROM task_time_entries e WHERE e.task_id=t.id AND e.user_id=pm.user_id) has_actual
                FROM tasks t JOIN projects p ON p.id=t.project_id
                JOIN project_members pm ON pm.project_id=p.id
                LEFT JOIN tasks parent ON parent.id=t.parent_task_id AND parent.project_id=t.project_id AND %s
                WHERE pm.user_id=? AND t.status='DONE' AND %s
                """.formatted(TaskVisibility.responsible("t","pm.user_id",false),
                        TaskVisibility.visibleTo("parent","pm.user_id",false),TaskVisibility.visibleTo("t","pm.user_id",false));
        List<Object> args=new ArrayList<>(); args.add(projects.user().id());
        if(start!=null) { sql+=" AND t.completed_at>=? AND t.completed_at<?";args.add(start.atStartOfDay());args.add(end.atStartOfDay()); }
        if(projectId!=null) { sql+=" AND t.project_id=?";args.add(projectId); }
        if(category!=null) { sql+=" AND t.planning_type=?";args.add(category.name()); }
        if(taskId!=null) { sql+=" AND (t.id=? OR t.parent_task_id=?)";args.add(taskId);args.add(taskId); }
        return db.query(sql,(r,n)->sample(r,false),args.toArray()).stream().filter(Objects::nonNull).toList();
    }

    private List<Sample> regular(LocalDate start,LocalDate end,Long projectId,PlanningType category,Long taskId) {
        String sql="""
                SELECT r.id,r.title,p.id project_id,p.name project_name,NULL::bigint parent_id,NULL::text parent_title,
                       c.occurrence_date completed_date,r.estimate_minutes,%s responsible,
                       COALESCE((SELECT SUM(e.minutes) FROM regular_task_time_entries e
                                 WHERE e.regular_task_id=r.id AND e.entry_date=c.occurrence_date AND e.user_id=pm.user_id),0) actual,
                       EXISTS(SELECT 1 FROM regular_task_time_entries e WHERE e.regular_task_id=r.id
                              AND e.entry_date=c.occurrence_date AND e.user_id=pm.user_id) has_actual
                FROM regular_tasks r JOIN regular_task_completions c ON c.regular_task_id=r.id
                JOIN projects p ON p.id=r.project_id JOIN project_members pm ON pm.project_id=p.id
                WHERE pm.user_id=? AND %s
                """.formatted(TaskVisibility.responsible("r","pm.user_id",true),TaskVisibility.visibleTo("r","pm.user_id",true));
        List<Object> args=new ArrayList<>(); args.add(projects.user().id());
        if(start!=null) { sql+=" AND c.occurrence_date>=? AND c.occurrence_date<?";args.add(start);args.add(end); }
        if(projectId!=null) { sql+=" AND r.project_id=?";args.add(projectId); }
        if(category!=null) { sql+=" AND r.planning_type=?";args.add(category.name()); }
        if(taskId!=null) { sql+=" AND r.id=?";args.add(taskId); }
        return db.query(sql,(r,n)->sample(r,true),args.toArray()).stream().filter(Objects::nonNull).toList();
    }

    private static Sample sample(ResultSet r,boolean recurring)throws SQLException {
        boolean responsible=r.getBoolean("responsible");
        long actual=r.getLong("actual");
        if(!responsible&&actual==0) return null;
        Integer estimate=responsible?(Integer)r.getObject("estimate_minutes"):null;
        boolean estimated=estimate!=null&&estimate>0;
        return new Sample(r.getLong("id"),recurring,r.getString("title"),r.getLong("project_id"),r.getString("project_name"),
                (Long)r.getObject("parent_id"),r.getString("parent_title"),r.getObject("completed_date",LocalDate.class),
                new TimeComparison(estimated?estimate:0,actual,1,estimated?0:1,r.getBoolean("has_actual")?0:1));
    }

    private record Key(Long id,boolean recurring) {}
    private record Sample(Long id,boolean recurring,String title,Long projectId,String projectName,
                          Long parentId,String parentTitle,LocalDate date,TimeComparison comparison) {}
}
