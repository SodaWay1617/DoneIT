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
                   created_at, updated_at, completed_at, closed_at
            FROM tasks
            """;

    private static final String INSERT_SQL = """
            INSERT INTO tasks (
                user_id, title, description, status, planned_for_at, deadline_at,
                created_at, updated_at, completed_at, closed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String UPDATE_SQL = """
            UPDATE tasks
            SET title = ?,
                description = ?,
                status = ?,
                planned_for_at = ?,
                deadline_at = ?,
                updated_at = ?,
                completed_at = ?,
                closed_at = ?
            WHERE id = ?
            """;

    private static final String FIND_BY_ID_SQL = BASE_SELECT + "WHERE id = ?";

    private static final String FIND_ACTIVE_FOR_DATE_SQL = BASE_SELECT + """
            WHERE user_id = ?
              AND status = 'OPEN'
              AND planned_for_at >= ?
              AND planned_for_at < ?
            ORDER BY planned_for_at, id
            """;

    private static final String FIND_BACKLOG_SQL = BASE_SELECT + """
            WHERE user_id = ?
              AND status = 'OPEN'
              AND planned_for_at IS NULL
            ORDER BY updated_at DESC, id DESC
            """;

    private static final String FIND_COMPLETED_OR_CLOSED_SQL = BASE_SELECT + """
            WHERE user_id = ?
              AND status IN ('DONE', 'CLOSED')
            ORDER BY updated_at DESC, id DESC
            """;

    private static final String MARK_DONE_SQL = """
            UPDATE tasks
            SET status = 'DONE',
                updated_at = ?,
                completed_at = ?,
                closed_at = NULL
            WHERE id = ?
              AND status = 'OPEN'
            """;

    private static final String MARK_CLOSED_SQL = """
            UPDATE tasks
            SET status = 'CLOSED',
                updated_at = ?,
                completed_at = NULL,
                closed_at = ?
            WHERE id = ?
              AND status = 'OPEN'
            """;

    private static final String RESCHEDULE_SQL = """
            UPDATE tasks
            SET planned_for_at = ?,
                updated_at = ?
            WHERE id = ?
            """;

    private static final String MOVE_TO_BACKLOG_SQL = """
            UPDATE tasks
            SET planned_for_at = NULL,
                updated_at = ?
            WHERE id = ?
            """;

    private static final String BULK_MOVE_SQL = """
            UPDATE tasks
            SET planned_for_at = planned_for_at + INTERVAL '1 day',
                updated_at = ?
            WHERE user_id = ?
              AND status = 'OPEN'
              AND planned_for_at >= ?
              AND planned_for_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TaskRowMapper taskRowMapper;

    public JdbcTaskRepository(JdbcTemplate jdbcTemplate, TaskRowMapper taskRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowMapper = taskRowMapper;
    }

    @Override
    public Task create(Task task) {
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
                task.closedAt()
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
        return jdbcTemplate.query(FIND_ACTIVE_FOR_DATE_SQL, taskRowMapper, userId, start, end);
    }

    @Override
    public List<Task> findBacklogTasks(Long userId) {
        return jdbcTemplate.query(FIND_BACKLOG_SQL, taskRowMapper, userId);
    }

    @Override
    public List<Task> findCompletedOrClosedTasks(Long userId) {
        return jdbcTemplate.query(FIND_COMPLETED_OR_CLOSED_SQL, taskRowMapper, userId);
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
    public int bulkMoveOpenDatedTasksToNextDay(Long userId, LocalDate date, LocalDateTime updatedAt) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return jdbcTemplate.update(BULK_MOVE_SQL, updatedAt, userId, start, end);
    }
}
