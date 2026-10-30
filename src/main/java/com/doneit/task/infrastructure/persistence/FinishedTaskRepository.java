package com.doneit.task.infrastructure.persistence;

import com.doneit.project.application.TaskVisibility;
import com.doneit.tag.application.TagService;
import com.doneit.task.application.view.FinishedFilter;
import com.doneit.task.domain.TaskPriority;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FinishedTaskRepository {
    private final NamedParameterJdbcTemplate db;
    public FinishedTaskRepository(NamedParameterJdbcTemplate db) { this.db = db; }

    public record Counts(long done, long closed) { public long total() { return done + closed; } }
    public record Row(Long id, boolean recurring, String title, String project, TaskStatus status,
                      TaskPriority priority, Integer estimateMinutes, Long actualMinutes,
                      LocalDateTime finishedAt, List<TagService.Tag> tags) {
        public String href(String returnTo) {
            return (recurring ? "/recurring/" : "/tasks/") + id + "/edit?redirectTo="
                    + java.net.URLEncoder.encode(returnTo, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public Counts counts(Long user, FinishedFilter filter) {
        return db.queryForObject("SELECT COUNT(*) FILTER (WHERE status='DONE') done,"
                        + "COUNT(*) FILTER (WHERE status='CLOSED') closed FROM (" + union(filter) + ") history",
                parameters(user, filter), (r, n) -> new Counts(r.getLong("done"), r.getLong("closed")));
    }

    public List<Row> page(Long user, FinishedFilter filter, long page) {
        var params = parameters(user, filter);
        params.put("limit", filter.size());
        params.put("offset", (page - 1) * filter.size());
        params.put("status", filter.status());
        // Pagination happens before loading tags or time entries; no whole-history Java collections.
        String sql = "SELECT * FROM (" + union(filter) + ") history"
                + (filter.status().equals("ALL") ? "" : " WHERE status=:status")
                + " ORDER BY finished_at DESC NULLS LAST,id DESC,recurring DESC LIMIT :limit OFFSET :offset";
        var rows = db.query(sql, params, (r, n) -> new Row(r.getLong("id"), r.getBoolean("recurring"),
                r.getString("title"), r.getString("project"), TaskStatus.valueOf(r.getString("status")),
                TaskPriority.valueOf(r.getString("priority")), (Integer) r.getObject("estimate_minutes"), null,
                r.getObject("finished_at", LocalDateTime.class), List.of()));
        List<Row> result = new ArrayList<>();
        var ordinaryTags = tags(user, rows.stream().filter(r -> !r.recurring()).map(Row::id).toList(), false);
        var regularTags = tags(user, rows.stream().filter(Row::recurring).map(Row::id).toList(), true);
        var ordinaryTime = time(rows.stream().filter(r -> !r.recurring()).map(Row::id).toList(), false);
        var regularTime = time(rows.stream().filter(Row::recurring).map(Row::id).toList(), true);
        for (var row : rows) result.add(new Row(row.id(), row.recurring(), row.title(), row.project(), row.status(),
                row.priority(), row.estimateMinutes(), (row.recurring() ? regularTime : ordinaryTime).get(row.id()),
                row.finishedAt(), (row.recurring() ? regularTags : ordinaryTags).getOrDefault(row.id(), List.of())));
        return List.copyOf(result);
    }

    private Map<Long, List<TagService.Tag>> tags(Long user, List<Long> ids, boolean regular) {
        Map<Long, List<TagService.Tag>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        String column = regular ? "regular_task_id" : "task_id";
        db.query("SELECT tt." + column + " task_id,tag.id,tag.name FROM task_tags tt JOIN tags tag ON tag.id=tt.tag_id"
                + " WHERE tag.user_id=:user AND tt." + column + " IN (:ids) ORDER BY LOWER(tag.name),tag.id",
                Map.of("user", user, "ids", ids), r -> {
                    result.computeIfAbsent(r.getLong("task_id"), ignored -> new ArrayList<>())
                            .add(new TagService.Tag(r.getLong("id"), r.getString("name"), 0));
                });
        return result;
    }

    private Map<Long, Long> time(List<Long> ids, boolean regular) {
        Map<Long, Long> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        String column = regular ? "regular_task_id" : "task_id";
        String table = regular ? "regular_task_time_entries" : "task_time_entries";
        db.query("SELECT " + column + " task_id,SUM(minutes) actual FROM " + table + " WHERE " + column
                + " IN (:ids) GROUP BY " + column, Map.of("ids", ids), r -> {
                    result.put(r.getLong("task_id"), r.getLong("actual"));
                });
        // Older ordinary tasks may have a stored total without individual entries.
        if (!regular) db.query("SELECT id,spent_minutes FROM tasks WHERE id IN (:ids) AND spent_minutes>0",
                Map.of("ids", ids), r -> { result.putIfAbsent(r.getLong("id"), r.getLong("spent_minutes")); });
        return result;
    }

    private static Map<String, Object> parameters(Long user, FinishedFilter filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        params.put("project", filter.projectId());
        params.put("tags", filter.tagIds());
        params.put("planning", filter.planningType());
        return params;
    }
    private static String union(FinishedFilter filter) { return source(false, filter) + " UNION ALL " + source(true, filter); }
    private static String source(boolean regular, FinishedFilter filter) {
        String table = regular ? "regular_tasks" : "tasks";
        String column = regular ? "regular_task_id" : "task_id";
        String date = regular ? "t.finished_at" : "CASE WHEN t.status='DONE' THEN t.completed_at ELSE t.closed_at END";
        return "SELECT t.id," + regular + " recurring,t.title,t.status,t.priority,t.estimate_minutes," + date + " finished_at,"
                + "CASE WHEN p.default_project THEN 'MAIN' ELSE split_part(p.name,'___',1) END project"
                + " FROM " + table + " t JOIN projects p ON p.id=t.project_id WHERE t.status IN ('DONE','CLOSED') AND "
                + TaskVisibility.memberTasks("t", regular).replace("?", ":user")
                + (filter.projectId() == null ? "" : " AND t.project_id=:project")
                + (filter.planningType() == null ? "" : " AND t.planning_type=:planning")
                + (filter.tagIds().isEmpty() ? "" : " AND EXISTS (SELECT 1 FROM task_tags tt JOIN tags tag ON tag.id=tt.tag_id"
                    + " WHERE tt." + column + "=t.id AND tag.user_id=:user AND tag.id IN (:tags))");
    }
}
