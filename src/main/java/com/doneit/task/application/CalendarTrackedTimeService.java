package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.project.application.TaskVisibility;
import com.doneit.task.application.view.PlanningLoad;
import com.doneit.task.domain.PlanningType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CalendarTrackedTimeService {
    private static final String ENTRIES = """
            SELECT e.entry_date,t.planning_type,e.minutes
            FROM %s e JOIN %s t ON t.id=e.%s
            WHERE e.user_id=? AND e.entry_date>=? AND e.entry_date<?
              AND (CAST(? AS bigint) IS NULL OR t.project_id=?)
              AND (CAST(? AS varchar) IS NULL OR t.planning_type=?)
              AND %s
            """;
    private static final String TOTALS = """
            SELECT entry_date,planning_type,SUM(minutes) minutes FROM (
            """ + ENTRIES.formatted("task_time_entries", "tasks", "task_id", TaskVisibility.memberTasks("t", false))
            + " UNION ALL "
            + ENTRIES.formatted("regular_task_time_entries", "regular_tasks", "regular_task_id", TaskVisibility.memberTasks("t", true))
            + ") entries GROUP BY entry_date,planning_type";

    private final JdbcTemplate db;
    private final ProjectService projects;

    public CalendarTrackedTimeService(JdbcTemplate db, ProjectService projects) {
        this.db = db;
        this.projects = projects;
    }

    public Map<LocalDate, PlanningLoad> forRange(LocalDate start, LocalDate end, Long projectId, PlanningType type) {
        Long user = projects.user().id();
        String planning = type == null ? null : type.name();
        Object[] args = {user,start,end,projectId,projectId,planning,planning,user,
                         user,start,end,projectId,projectId,planning,planning,user};
        Map<LocalDate, PlanningLoad> result = new HashMap<>();
        db.query(TOTALS, rs -> {
            LocalDate date = rs.getObject("entry_date", LocalDate.class);
            // Corrections are signed entries; aggregate them before displaying the daily total.
            int minutes = rs.getInt("minutes");
            PlanningType category = PlanningType.valueOf(rs.getString("planning_type"));
            result.put(date, result.getOrDefault(date, PlanningLoad.empty()).add(category, minutes));
        }, args);
        return Map.copyOf(result);
    }
}
