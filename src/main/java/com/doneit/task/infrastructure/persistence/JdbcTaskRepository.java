package com.doneit.task.infrastructure.persistence;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTaskRepository implements TaskRepository {

    private static final String BASE_SELECT = """
            SELECT id, user_id, title, description, status, planned_for_at, deadline_at,
                   created_at, updated_at, completed_at, closed_at,
                   project_id, task_number, task_key, planned_date, priority
            FROM tasks
            """;

    private static final String INSERT_SQL = """
            INSERT INTO tasks (
                user_id, title, description, status, planned_for_at, deadline_at,
                created_at, updated_at, completed_at, closed_at,
                project_id, task_number, task_key, planned_date, priority
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UPDATE_SQL = """
            UPDATE tasks
            SET title = ?,
                description = ?,
                status = ?,
                planned_for_at = ?,
                deadline_at = ?,
                project_id = ?,
                planned_date = ?,
                priority = ?,
                updated_at = ?,
                completed_at = ?,
                closed_at = ?
            WHERE id = ?
            """;

    private static final String FIND_BY_ID_SQL = BASE_SELECT + "WHERE id = ?";

    private static final String FIND_ACTIVE_FOR_DATE_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('SPECIFICATION', 'TODO', 'IN_PROGRESS', 'DOCUMENTATION')
              AND (
                  (planned_for_at IS NULL AND planned_date IS NULL)
                  OR (planned_for_at >= ? AND planned_for_at < ?)
                  OR planned_date = ?
              )
            ORDER BY CASE priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END DESC,
                     CASE WHEN planned_for_at IS NULL THEN 1 ELSE 0 END,
                     CAST(planned_for_at AS time),
                     CASE status WHEN 'IN_PROGRESS' THEN 0 WHEN 'TODO' THEN 1 WHEN 'DOCUMENTATION' THEN 2
                         WHEN 'SPECIFICATION' THEN 3 ELSE 4 END,
                     LOWER(title), id
            """;

    private static final String FIND_ACTIVE_DUE_BY_DATE_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('SPECIFICATION', 'TODO', 'IN_PROGRESS', 'DOCUMENTATION')
              AND (
                  (planned_for_at IS NULL AND planned_date IS NULL)
                  OR planned_for_at < ?
                  OR planned_date <= ?
              )
            ORDER BY CASE priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END DESC,
                     CASE WHEN planned_for_at IS NULL THEN 1 ELSE 0 END,
                     CAST(planned_for_at AS time),
                     CASE status WHEN 'IN_PROGRESS' THEN 0 WHEN 'TODO' THEN 1 WHEN 'DOCUMENTATION' THEN 2
                         WHEN 'SPECIFICATION' THEN 3 ELSE 4 END,
                     LOWER(title), id
            """;

    private static final String FIND_BACKLOG_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('BACKLOG', 'PAUSED')
            ORDER BY CASE priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END DESC,
                     updated_at DESC, id DESC
            """;

    private static final String FIND_INBOX_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status = 'NEW'
            ORDER BY CASE priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'NORMAL' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END DESC,
                     updated_at DESC, id DESC
            """;

    private static final String FIND_COMPLETED_OR_CLOSED_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('DONE', 'CLOSED')
            ORDER BY updated_at DESC, id DESC
            """;

    private static final String FIND_COMPLETED_OR_CLOSED_FOR_DATE_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('DONE', 'CLOSED')
              AND ((planned_for_at >= ? AND planned_for_at < ?) OR planned_date = ?)
            ORDER BY planned_for_at, updated_at DESC, id
            """;

    private static final String FIND_FOR_CALENDAR_RANGE_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('SPECIFICATION', 'TODO', 'IN_PROGRESS', 'DOCUMENTATION')
              AND (
                  (planned_for_at IS NOT NULL AND planned_for_at >= ? AND planned_for_at < ?)
                  OR (planned_date IS NOT NULL AND planned_date >= ? AND planned_date < ?)
                  OR (deadline_at IS NOT NULL AND deadline_at >= ? AND deadline_at < ?)
                  OR (planned_for_at IS NULL AND planned_date IS NULL AND deadline_at IS NULL)
              )
            ORDER BY COALESCE(planned_for_at, deadline_at), id
            """;

    private static final String FIND_FOR_KANBAN_SQL = BASE_SELECT + """
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND (
                  status = 'BACKLOG'
                  OR (status NOT IN ('DONE', 'CLOSED') AND (
                      (planned_for_at IS NULL AND planned_date IS NULL)
                      OR (planned_for_at >= ? AND planned_for_at < ?)
                      OR planned_date = ?
                  ))
                  OR (status IN ('DONE', 'CLOSED') AND (
                      (planned_for_at >= ? AND planned_for_at < ?)
                      OR planned_date = ?
                  ))
              )
            ORDER BY CASE status
                         WHEN 'NEW' THEN 0 WHEN 'BACKLOG' THEN 1 WHEN 'PAUSED' THEN 2
                         WHEN 'SPECIFICATION' THEN 3 WHEN 'TODO' THEN 4 WHEN 'IN_PROGRESS' THEN 5
                         WHEN 'DOCUMENTATION' THEN 6 WHEN 'DONE' THEN 7 ELSE 8
                     END,
                     status_position, id
            """;

    private static final String MARK_DONE_SQL = """
            UPDATE tasks
            SET status = 'DONE',
                updated_at = ?,
                completed_at = ?,
                closed_at = NULL
            WHERE id = ?
              AND status NOT IN ('DONE', 'CLOSED')
            """;

    private static final String MARK_CLOSED_SQL = """
            UPDATE tasks
            SET status = 'CLOSED',
                updated_at = ?,
                completed_at = NULL,
                closed_at = ?
            WHERE id = ?
              AND status NOT IN ('DONE', 'CLOSED')
            """;

    private static final String RESCHEDULE_SQL = """
            UPDATE tasks
            SET status = CASE WHEN status IN ('NEW', 'BACKLOG') THEN 'TODO' ELSE status END,
                planned_for_at = ?,
                planned_date = NULL,
                updated_at = ?
            WHERE id = ?
            """;

    private static final String MOVE_TO_BACKLOG_SQL = """
            UPDATE tasks
            SET status = 'BACKLOG',
                planned_for_at = NULL,
                planned_date = NULL,
                updated_at = ?
            WHERE id = ?
            """;

    private static final String BULK_MOVE_SQL = """
            UPDATE tasks
            SET planned_for_at = planned_for_at + INTERVAL '1 day',
                planned_date = planned_date + 1,
                updated_at = ?
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('SPECIFICATION', 'TODO', 'IN_PROGRESS', 'DOCUMENTATION')
              AND ((planned_for_at IS NOT NULL AND planned_for_at < ?) OR planned_date < CAST(? AS date))
            """;

    private static final String BULK_MOVE_OVERDUE_TO_DATE_SQL = """
            UPDATE tasks
            SET planned_for_at = CAST(? AS date) + CAST(planned_for_at AS time),
                planned_date = CASE WHEN planned_date IS NULL THEN NULL ELSE CAST(? AS date) END,
                updated_at = ?
            WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=tasks.project_id AND pm.user_id=?)
              AND status IN ('SPECIFICATION', 'TODO', 'IN_PROGRESS', 'DOCUMENTATION')
              AND ((planned_for_at IS NOT NULL AND planned_for_at < ?) OR planned_date < CAST(? AS date))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TaskRowMapper taskRowMapper;

    public JdbcTaskRepository(JdbcTemplate jdbcTemplate, TaskRowMapper taskRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowMapper = taskRowMapper;
    }

    @Override
    public Task create(Task task) {
        Long projectId = task.projectId();
        if (projectId == null) {
            projectId = jdbcTemplate.queryForObject(\u0022SELECT id FROM projects WHERE owner_user_id=? AND default_project\u0022, Long.class, task.userId());
        }
        String code = jdbcTemplate.queryForObject(\u0022SELECT code FROM projects p JOIN project_members m ON m.project_id=p.id WHERE p.id=? AND m.user_id=?\u0022, String.class, projectId, task.userId());
        Long number = jdbcTemplate.queryForObject(\u0022UPDATE projects SET next_task_number=next_task_number+1 WHERE id=? RETURNING next_task_number-1\u0022, Long.class, projectId);
        String login = jdbcTemplate.queryForObject(\u0022SELECT login FROM users WHERE id=?\u0022, String.class, task.userId());
        Long id = jdbcTemplate.queryForObject(
                INSERT_SQL,
                Long.class,
                task.userId(),
                task.title(),
                task.description(),
                task.status().name(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.createdAt(),
                task.updatedAt(),
                task.completedAt(),
                task.closedAt(), projectId, number,
                code + \u0022-\u0022 + number + \u0022___\u0022 + login,
                task.plannedDate(),
                task.priority().name()
        );
        return findById(Objects.requireNonNull(id)).orElseThrow();
    }

    @Override
    public Task update(Task task) {
        jdbcTemplate.update(
                UPDATE_SQL,
                task.title(),
                task.description(),
                task.status().name(),
                task.plannedForAt(),
                task.deadlineAt(),
                task.projectId(),
                task.plannedDate(),
                task.priority().name(),
                task.updatedAt(),
                task.completedAt(),
                task.closedAt(),
                task.id()
        );
        return findById(task.id()).orElseThrow();
    }

    @Override
    public Optional<Task> findById(Long id) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, taskRowMapper, id).stream().findFirst();
    }

    @Override
    public List<Task> findActiveTasksForDate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return jdbcTemplate.query(FIND_ACTIVE_FOR_DATE_SQL, taskRowMapper, userId, start, end, date);
    }

    @Override
    public List<Task> findActiveTasksDueByDate(Long userId, LocalDate date) {
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return jdbcTemplate.query(FIND_ACTIVE_DUE_BY_DATE_SQL, taskRowMapper, userId, end, date);
    }

    @Override
    public List<Task> findBacklogTasks(Long userId) {
        return jdbcTemplate.query(FIND_BACKLOG_SQL, taskRowMapper, userId);
    }

    @Override
    public List<Task> findInboxTasks(Long userId) {
        return jdbcTemplate.query(FIND_INBOX_SQL, taskRowMapper, userId);
    }

    @Override
    public List<Task> findCompletedOrClosedTasks(Long userId) {
        return jdbcTemplate.query(FIND_COMPLETED_OR_CLOSED_SQL, taskRowMapper, userId);
    }

    @Override
    public List<Task> findCompletedOrClosedTasksForDate(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return jdbcTemplate.query(FIND_COMPLETED_OR_CLOSED_FOR_DATE_SQL, taskRowMapper, userId, start, end, date);
    }

    @Override
    public List<Task> findTasksForCalendarRange(Long userId, LocalDate startDate, LocalDate endDateExclusive) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDateExclusive.atStartOfDay();
        return jdbcTemplate.query(FIND_FOR_CALENDAR_RANGE_SQL, taskRowMapper, userId, start, end, startDate, endDateExclusive, start, end);
    }

    @Override
    public List<Task> findTasksForKanban(Long userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return jdbcTemplate.query(
                FIND_FOR_KANBAN_SQL,
                taskRowMapper,
                userId, start, end, date, start, end, date
        );
    }

    @Override
    public void reorderKanban(Long userId, com.doneit.task.domain.TaskStatus status, List<Long> orderedTaskIds,
                              LocalDateTime updatedAt) {
        for (Long taskId : orderedTaskIds) {
            jdbcTemplate.update("""
                    UPDATE tasks
                    SET status = ?,
                        planned_for_at = CASE WHEN ? = 'BACKLOG' THEN NULL ELSE planned_for_at END,
                        planned_date = CASE WHEN ? = 'BACKLOG' THEN NULL ELSE planned_date END,
                        completed_at = CASE WHEN ? = 'DONE' THEN COALESCE(completed_at, ?) ELSE NULL END,
                        closed_at = CASE WHEN ? = 'CLOSED' THEN COALESCE(closed_at, ?) ELSE NULL END,
                        updated_at = ?
                    WHERE id = ?
                      AND EXISTS (
                          SELECT 1 FROM project_members pm
                          WHERE pm.project_id = tasks.project_id AND pm.user_id = ?
                      )
                    """, status.name(), status.name(), status.name(), status.name(), updatedAt, status.name(), updatedAt,
                    updatedAt, taskId, userId);
        }
        for (int index = 0; index < orderedTaskIds.size(); index++) {
            jdbcTemplate.update("""
                    UPDATE tasks
                    SET status_position = ?
                    WHERE id = ?
                      AND status = ?
                      AND EXISTS (
                          SELECT 1 FROM project_members pm
                          WHERE pm.project_id = tasks.project_id AND pm.user_id = ?
                      )
                    """, index + 1L, orderedTaskIds.get(index), status.name(), userId);
        }
    }

    @Override
    public Optional<Task> markDone(Long taskId, LocalDateTime completedAt) {
        int updated = jdbcTemplate.update(MARK_DONE_SQL, completedAt, completedAt, taskId);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(taskId);
    }

    @Override
    public Optional<Task> markClosed(Long taskId, LocalDateTime closedAt) {
        int updated = jdbcTemplate.update(MARK_CLOSED_SQL, closedAt, closedAt, taskId);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(taskId);
    }

    @Override
    public Optional<Task> reschedule(Long taskId, LocalDateTime plannedForAt, LocalDateTime updatedAt) {
        int updated = jdbcTemplate.update(RESCHEDULE_SQL, plannedForAt, updatedAt, taskId);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(taskId);
    }

    @Override
    public Optional<Task> moveToBacklog(Long taskId, LocalDateTime updatedAt) {
        int updated = jdbcTemplate.update(MOVE_TO_BACKLOG_SQL, updatedAt, taskId);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(taskId);
    }

    @Override
    public Optional<Task> moveToProject(Long taskId, Long projectId, LocalDateTime updatedAt) {
        int updated = jdbcTemplate.update(\u0022UPDATE tasks SET project_id=?,updated_at=? WHERE id=?\u0022, projectId, updatedAt, taskId);
        return updated == 0 ? Optional.empty() : findById(taskId);
    }

    @Override
    public int bulkMoveOpenDatedTasksToNextDay(Long userId, LocalDate date, LocalDateTime updatedAt) {
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return jdbcTemplate.update(BULK_MOVE_SQL, updatedAt, userId, end, end.toLocalDate());
    }

    @Override
    public int bulkMoveOverdueOpenDatedTasksToDate(Long userId, LocalDate date, LocalDateTime updatedAt) {
        LocalDateTime start = date.atStartOfDay();
        return jdbcTemplate.update(BULK_MOVE_OVERDUE_TO_DATE_SQL, date, date, updatedAt, userId, start, date);
    }
}
