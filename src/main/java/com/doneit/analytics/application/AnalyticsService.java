package com.doneit.analytics.application;

import com.doneit.project.application.ProjectService;
import com.doneit.user.application.AnalyticsSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final String REGULAR_ENTRIES = """
              UNION ALL
              SELECT r.id,TRUE,r.title,p.id,p.name,e.minutes
              FROM regular_task_time_entries e JOIN regular_tasks r ON r.id=e.regular_task_id
              JOIN projects p ON p.id=r.project_id JOIN project_members pm ON pm.project_id=p.id
              WHERE pm.user_id=? AND e.user_id=? AND e.entry_date>=? AND e.entry_date<?
            )
            """;
    private static final String SEPARATE_ENTRIES = """
            WITH entries AS (
              SELECT t.id task_id,FALSE recurring,t.title,p.id project_id,p.name project_name,e.minutes
              FROM task_time_entries e JOIN tasks t ON t.id=e.task_id
              JOIN projects p ON p.id=t.project_id JOIN project_members pm ON pm.project_id=p.id
              WHERE pm.user_id=? AND e.user_id=? AND e.entry_date>=? AND e.entry_date<?
            """ + REGULAR_ENTRIES;
    private static final String ROLLED_UP_ENTRIES = """
            WITH entries AS (
              SELECT COALESCE(parent.id,t.id) task_id,FALSE recurring,
                     COALESCE(parent.title,t.title) title,p.id project_id,p.name project_name,e.minutes
              FROM task_time_entries e JOIN tasks t ON t.id=e.task_id
              LEFT JOIN tasks parent ON parent.id=t.parent_task_id
              JOIN projects p ON p.id=t.project_id JOIN project_members pm ON pm.project_id=p.id
              WHERE pm.user_id=? AND e.user_id=? AND e.entry_date>=? AND e.entry_date<?
            """ + REGULAR_ENTRIES;
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final AnalyticsSettingsService settings;

    public AnalyticsService(JdbcTemplate db, ProjectService projects) {
        this(db, projects, null);
    }
    @Autowired
    public AnalyticsService(JdbcTemplate db, ProjectService projects, AnalyticsSettingsService settings) {
        this.db = db; this.projects = projects;
        this.settings = settings;
    }

    public MonthlyAnalyticsView month(YearMonth month) {
        LocalDate start = month.atDay(1), end = month.plusMonths(1).atDay(1);
        Long userId = projects.user().id();
        Object[] args = {userId,userId,start,end,userId,userId,start,end};
        String entries = settings != null && settings.rollUpSubtaskTime() ? ROLLED_UP_ENTRIES : SEPARATE_ENTRIES;
        List<RawTask> tasks = db.query(entries + """
                SELECT task_id,recurring,title,project_name,SUM(minutes) minutes FROM entries
                GROUP BY task_id,recurring,title,project_name HAVING SUM(minutes)>0
                ORDER BY minutes DESC,LOWER(title) LIMIT 10
                """, (r,n) -> new RawTask(r.getLong("task_id"),r.getBoolean("recurring"),
                r.getString("title"),r.getString("project_name"),r.getInt("minutes")), args);
        List<RawProject> projectRows = db.query(entries + """
                SELECT project_id,project_name,SUM(minutes) minutes FROM entries
                GROUP BY project_id,project_name HAVING SUM(minutes)>0
                ORDER BY minutes DESC,LOWER(project_name)
                """, (r,n) -> new RawProject(r.getLong("project_id"),r.getString("project_name"),
                r.getInt("minutes")), args);
        int total = projectRows.stream().mapToInt(RawProject::minutes).sum();
        List<TaskTimeView> top = tasks.stream().map(t -> new TaskTimeView(t.id,t.recurring,t.title,
                t.project,t.minutes,percentage(t.minutes,total))).toList();
        List<ProjectTimeView> byProject = projectRows.stream().map(p -> new ProjectTimeView(p.id,p.name,
                p.minutes,percentage(p.minutes,total))).toList();
        return new MonthlyAnalyticsView(month,month.toString(),month.minusMonths(1).toString(),
                month.plusMonths(1).toString(),total,top,byProject);
    }

    private static int percentage(int value,int total) {
        return total == 0 ? 0 : (int)Math.round(value * 100.0 / total);
    }
    private record RawTask(Long id,boolean recurring,String title,String project,int minutes) {}
    private record RawProject(Long id,String name,int minutes) {}
}
