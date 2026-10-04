package com.doneit.task.application;

import com.doneit.project.application.ProjectService;
import com.doneit.task.domain.TaskStatus;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubtaskService {
    private final JdbcTemplate db;
    private final ProjectService projects;
    public SubtaskService(JdbcTemplate db, ProjectService projects) { this.db = db; this.projects = projects; }

    public SubtaskSummary summary(Long parentId) {
        accessibleProject(parentId);
        List<SubtaskItem> children = db.query("""
                SELECT id,title,status,estimate_minutes,spent_minutes
                FROM tasks WHERE parent_task_id=? ORDER BY status,created_at,id
                """, (rs, row) -> new SubtaskItem(rs.getLong("id"), rs.getString("title"),
                TaskStatus.valueOf(rs.getString("status")), (Integer) rs.getObject("estimate_minutes"),
                rs.getInt("spent_minutes")), parentId);
        int estimate = children.stream().filter(c -> c.estimateMinutes() != null).mapToInt(SubtaskItem::estimateMinutes).sum();
        int spent = children.stream().mapToInt(SubtaskItem::spentMinutes).sum();
        return new SubtaskSummary(children, estimate, spent);
    }

    public ParentLink parentOf(Long childId) {
        accessibleProject(childId);
        return db.query("SELECT p.id,p.title FROM tasks c JOIN tasks p ON p.id=c.parent_task_id WHERE c.id=?",
                (rs, row) -> new ParentLink(rs.getLong("id"), rs.getString("title")), childId)
                .stream().findFirst().orElse(null);
    }

    @Transactional
    public void attach(Long childId, Long parentId) {
        Long childProject = accessibleProject(childId), parentProject = accessibleProject(parentId);
        if (childId.equals(parentId) || !childProject.equals(parentProject))
            throw new IllegalArgumentException("Parent and subtask must be different tasks in the same project");
        int changed = db.update("""
                UPDATE tasks SET parent_task_id=? WHERE id=? AND parent_task_id IS NULL
                AND NOT EXISTS(SELECT 1 FROM tasks parent WHERE parent.id=? AND parent.parent_task_id IS NOT NULL)
                """, parentId, childId, parentId);
        if (changed != 1) throw new IllegalArgumentException("Only root tasks can contain subtasks");
    }

    private Long accessibleProject(Long taskId) {
        Long projectId = db.query("SELECT project_id FROM tasks WHERE id=?", (rs, row) -> rs.getLong(1), taskId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Task not found"));
        projects.get(projectId);
        return projectId;
    }

    public record SubtaskSummary(List<SubtaskItem> tasks, int estimateMinutes, int spentMinutes) {}
    public record SubtaskItem(Long id, String title, TaskStatus status, Integer estimateMinutes, int spentMinutes) {}
    public record ParentLink(Long id, String title) {}
}
