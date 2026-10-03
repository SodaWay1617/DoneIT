package com.doneit.comment.application;

import com.doneit.project.application.ProjectService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskCommentService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    private final Clock clock;

    public TaskCommentService(JdbcTemplate db, ProjectService projects, Clock clock) {
        this.db = db; this.projects = projects; this.clock = clock;
    }

    public List<TaskCommentView> forTask(Long taskId) {
        requireTaskAccess(taskId);
        return db.query("""
                SELECT c.id,u.display_name,u.login,c.body,c.created_at
                FROM task_comments c JOIN users u ON u.id=c.user_id
                WHERE c.task_id=? ORDER BY c.created_at,c.id
                """, (r,n) -> new TaskCommentView(r.getLong("id"),
                r.getString("display_name")==null?r.getString("login"):r.getString("display_name"),
                r.getString("body"),r.getObject("created_at",LocalDateTime.class)), taskId);
    }

    public List<TaskCommentView> forRegularTask(Long taskId) {
        requireRegularTaskAccess(taskId);
        return db.query("""
                SELECT c.id,u.display_name,u.login,c.body,c.created_at
                FROM task_comments c JOIN users u ON u.id=c.user_id
                WHERE c.regular_task_id=? ORDER BY c.created_at,c.id
                """, (r,n) -> new TaskCommentView(r.getLong("id"),
                r.getString("display_name")==null?r.getString("login"):r.getString("display_name"),
                r.getString("body"),r.getObject("created_at",LocalDateTime.class)), taskId);
    }

    @Transactional
    public void addToTask(Long taskId, String body) {
        requireTaskAccess(taskId); insert(body, taskId, null);
    }

    @Transactional
    public void addToRegularTask(Long taskId, String body) {
        requireRegularTaskAccess(taskId); insert(body, null, taskId);
    }

    private void insert(String body, Long taskId, Long regularTaskId) {
        if (body == null || body.isBlank()) throw new IllegalArgumentException("Comment is required");
        db.update("INSERT INTO task_comments(user_id,task_id,regular_task_id,body,created_at) VALUES(?,?,?,?,?)",
                projects.user().id(), taskId, regularTaskId, body.trim(), LocalDateTime.now(clock));
    }

    private void requireTaskAccess(Long id) {
        Long projectId = db.query("SELECT project_id FROM tasks WHERE id=?", (r,n)->r.getLong(1), id)
                .stream().findFirst().orElseThrow();
        projects.get(projectId);
    }

    private void requireRegularTaskAccess(Long id) {
        Long projectId = db.query("SELECT project_id FROM regular_tasks WHERE id=?", (r,n)->r.getLong(1), id)
                .stream().findFirst().orElseThrow();
        projects.get(projectId);
    }
}
