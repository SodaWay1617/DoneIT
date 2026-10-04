package com.doneit.assignment.application;

import com.doneit.project.application.ProjectService;
import com.doneit.user.domain.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskAssignmentService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final Clock clock;

    public TaskAssignmentService(JdbcTemplate db, ProjectService projects, Clock clock) {
        this.db = db; this.projects = projects; this.clock = clock;
    }

    public AssignmentForm assignmentForTask(Long id) { return form(id, false); }
    public AssignmentForm assignmentForRegularTask(Long id) { return form(id, true); }
    @Transactional public void assignTask(Long id, List<Long> users) { replace(id, users, false); }
    @Transactional public void assignRegularTask(Long id, List<Long> users) { replace(id, users, true); }
    public Map<Long, List<String>> visibleTaskAssignees() { return visible(false); }
    public Map<Long, List<String>> visibleRegularTaskAssignees() { return visible(true); }

    private AssignmentForm form(Long id, boolean regular) {
        Long projectId = project(id, regular);
        String column = regular ? "regular_task_id" : "task_id";
        List<Long> assigned = db.query("SELECT user_id FROM task_assignees WHERE " + column + "=? ORDER BY assigned_at,user_id",
                (rs, row) -> rs.getLong(1), id);
        return new AssignmentForm(projects.members(projectId), assigned);
    }

    private void replace(Long id, List<Long> requested, boolean regular) {
        Long projectId = project(id, regular);
        Set<Long> ids = new LinkedHashSet<>(requested == null ? List.of() : requested);
        Set<Long> members = projects.members(projectId).stream().map(User::id).collect(java.util.stream.Collectors.toSet());
        if (!members.containsAll(ids)) throw new IllegalArgumentException("Every assignee must be a project member");
        String column = regular ? "regular_task_id" : "task_id";
        db.update("DELETE FROM task_assignees WHERE " + column + "=?", id);
        LocalDateTime now = LocalDateTime.now(clock);
        ids.forEach(userId -> db.update("INSERT INTO task_assignees(" + column + ",user_id,assigned_at) VALUES(?,?,?)", id, userId, now));
    }

    private Long project(Long id, boolean regular) {
        String table = regular ? "regular_tasks" : "tasks";
        Long projectId = db.query("SELECT project_id FROM " + table + " WHERE id=?", (rs, row) -> rs.getLong(1), id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Task not found"));
        projects.get(projectId);
        return projectId;
    }

    private Map<Long, List<String>> visible(boolean regular) {
        String column = regular ? "regular_task_id" : "task_id";
        String table = regular ? "regular_tasks" : "tasks";
        List<Row> rows = db.query("""
                SELECT a.%s task_id,COALESCE(NULLIF(u.display_name,''),u.login) assignee
                FROM task_assignees a JOIN %s t ON t.id=a.%s
                JOIN project_members viewer ON viewer.project_id=t.project_id AND viewer.user_id=?
                JOIN users u ON u.id=a.user_id
                ORDER BY a.%s,COALESCE(NULLIF(u.display_name,''),u.login)
                """.formatted(column, table, column, column),
                (rs, row) -> new Row(rs.getLong("task_id"), rs.getString("assignee")), projects.user().id());
        Map<Long, List<String>> result = new LinkedHashMap<>();
        rows.forEach(row -> result.computeIfAbsent(row.id(), ignored -> new ArrayList<>()).add(row.name()));
        return result;
    }

    public record AssignmentForm(List<User> members, List<Long> assignedUserIds) {}
    private record Row(Long id, String name) {}
}
