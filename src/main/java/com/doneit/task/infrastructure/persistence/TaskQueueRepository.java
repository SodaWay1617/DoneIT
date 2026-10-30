package com.doneit.task.infrastructure.persistence;

import com.doneit.project.application.TaskVisibility;
import com.doneit.task.application.view.TaskListPaging;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaskQueueRepository {
    private final NamedParameterJdbcTemplate db;
    public TaskQueueRepository(NamedParameterJdbcTemplate db) { this.db = db; }
    public record Entry(Long id, boolean recurring, String title, String taskKey, String project,
                        LocalDateTime createdAt, Integer estimateMinutes) {}
    public record Page(List<Entry> rows, TaskListPaging paging) {}

    public Page page(Long user, boolean backlog, Long project, String planning, int size, long requested) {
        TaskListPaging.validate(size, requested);
        var params = new HashMap<String, Object>();
        params.put("user", user); params.put("project", project); params.put("planning", planning);
        String source = source(false, backlog, project, planning);
        if (backlog) source += " UNION ALL " + source(true, true, project, planning);
        Long total = db.queryForObject("SELECT COUNT(*) FROM (" + source + ") queue", params, Long.class);
        var paging = TaskListPaging.of(backlog ? "/backlog" : "/inbox", project, planning, size, total, requested);
        params.put("limit", size); params.put("offset", (paging.number() - 1) * size);
        String order = backlog ? "created_at DESC,id DESC,recurring ASC"
                : "priority_rank DESC,updated_at DESC,id DESC";
        var rows = db.query("SELECT * FROM (" + source + ") queue ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                params, (r, n) -> new Entry(r.getLong("id"), r.getBoolean("recurring"), r.getString("title"),
                        r.getString("task_key"), r.getString("project"), r.getObject("created_at", LocalDateTime.class),
                        (Integer) r.getObject("estimate_minutes")));
        return new Page(List.copyOf(rows), paging);
    }

    private static String source(boolean regular, boolean backlog, Long project, String planning) {
        return "SELECT t.id," + regular + " recurring,t.title,t.task_key,t.created_at,t.updated_at,t.estimate_minutes,"
                + "CASE t.priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END priority_rank,"
                + "CASE WHEN p.default_project THEN 'MAIN' ELSE split_part(p.name,'___',1) END project FROM "
                + (regular ? "regular_tasks" : "tasks") + " t JOIN projects p ON p.id=t.project_id WHERE "
                + (backlog ? (regular ? "t.status='BACKLOG'" : "t.status IN ('BACKLOG','PAUSED')") : "t.status='NEW'")
                + " AND " + TaskVisibility.memberTasks("t", regular).replace("?", ":user")
                + (project == null ? "" : " AND t.project_id=:project")
                + (planning == null ? "" : " AND t.planning_type=:planning");
    }
}
