package com.doneit.task.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doneit.task.domain.Task;
import com.doneit.task.domain.TaskRepository;
import com.doneit.task.domain.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class JdbcTaskRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskRepository taskRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM users");
        userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (login, password_hash, display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "task-owner",
                "hash",
                "Task Owner",
                LocalDateTime.of(2026, 4, 5, 18, 0),
                LocalDateTime.of(2026, 4, 5, 18, 0)
        );
    }

    @Test
    void createsAndLoadsTaskById() {
        Task created = taskRepository.create(openTask("Buy milk", LocalDateTime.of(2026, 4, 6, 10, 0)));

        assertNotNull(created.id());
        assertEquals("Buy milk", taskRepository.findById(created.id()).orElseThrow().title());
    }

    @Test
    void updatesTaskFields() {
        Task created = taskRepository.create(openTask("Old title", LocalDateTime.of(2026, 4, 6, 10, 0)));
        Task updated = new Task(
                created.id(),
                created.userId(),
                "New title",
                "Updated description",
                TaskStatus.OPEN,
                LocalDateTime.of(2026, 4, 6, 12, 0),
                LocalDateTime.of(2026, 4, 7, 18, 0),
                created.createdAt(),
                LocalDateTime.of(2026, 4, 5, 19, 0),
                null,
                null
        );

        Task persisted = taskRepository.update(updated);

        assertEquals("New title", persisted.title());
        assertEquals(LocalDateTime.of(2026, 4, 6, 12, 0), persisted.plannedForAt());
    }

    @Test
    void findsOnlyOpenDatedTasksForSelectedDate() {
        taskRepository.create(openTask("Today open", LocalDateTime.of(2026, 4, 6, 9, 0)));
        taskRepository.create(openTask("Backlog", null));
        taskRepository.create(openTask("Other day", LocalDateTime.of(2026, 4, 7, 9, 0)));
        Task doneTask = taskRepository.create(openTask("Done same day", LocalDateTime.of(2026, 4, 6, 12, 0)));
        taskRepository.markDone(doneTask.id(), LocalDateTime.of(2026, 4, 5, 20, 0));

        List<Task> tasks = taskRepository.findActiveTasksForDate(userId, LocalDate.of(2026, 4, 6));

        assertEquals(1, tasks.size());
        assertEquals("Today open", tasks.getFirst().title());
    }

    @Test
    void findsBacklogTasksSeparately() {
        taskRepository.create(openTask("Backlog one", null));
        taskRepository.create(openTask("Scheduled", LocalDateTime.of(2026, 4, 6, 11, 0)));

        List<Task> backlog = taskRepository.findBacklogTasks(userId);

        assertEquals(1, backlog.size());
        assertTrue(backlog.getFirst().isBacklog());
    }

    @Test
    void findsCompletedAndClosedTasksSeparately() {
        Task doneTask = taskRepository.create(openTask("Done task", LocalDateTime.of(2026, 4, 6, 11, 0)));
        Task closedTask = taskRepository.create(openTask("Closed task", LocalDateTime.of(2026, 4, 6, 12, 0)));
        taskRepository.markDone(doneTask.id(), LocalDateTime.of(2026, 4, 5, 20, 10));
        taskRepository.markClosed(closedTask.id(), LocalDateTime.of(2026, 4, 5, 20, 15));

        List<Task> completed = taskRepository.findCompletedOrClosedTasks(userId);

        assertEquals(2, completed.size());
        assertTrue(completed.stream().allMatch(task -> task.status() != TaskStatus.OPEN));
    }

    @Test
    void updatesStatusThroughDedicatedMethods() {
        Task created = taskRepository.create(openTask("Status task", LocalDateTime.of(2026, 4, 6, 13, 0)));

        Task done = taskRepository.markDone(created.id(), LocalDateTime.of(2026, 4, 5, 20, 20)).orElseThrow();
        Task closedCandidate = taskRepository.create(openTask("Close me", LocalDateTime.of(2026, 4, 6, 14, 0)));
        Task closed = taskRepository.markClosed(closedCandidate.id(), LocalDateTime.of(2026, 4, 5, 20, 25)).orElseThrow();

        assertEquals(TaskStatus.DONE, done.status());
        assertEquals(TaskStatus.CLOSED, closed.status());
    }

    @Test
    void reschedulesTaskAndMovesItOutOfBacklog() {
        Task created = taskRepository.create(openTask("Backlog item", null));

        Task rescheduled = taskRepository.reschedule(
                created.id(),
                LocalDateTime.of(2026, 4, 6, 16, 0),
                LocalDateTime.of(2026, 4, 5, 20, 30)
        ).orElseThrow();

        assertFalse(rescheduled.isBacklog());
        assertEquals(LocalDateTime.of(2026, 4, 6, 16, 0), rescheduled.plannedForAt());
    }

    @Test
    void movesTaskToBacklogByClearingPlannedDate() {
        Task created = taskRepository.create(openTask("Move me", LocalDateTime.of(2026, 4, 6, 17, 0)));

        Task moved = taskRepository.moveToBacklog(created.id(), LocalDateTime.of(2026, 4, 5, 20, 35)).orElseThrow();

        assertTrue(moved.isBacklog());
        assertEquals(null, moved.plannedForAt());
    }

    @Test
    void bulkMoveShiftsOnlyOpenDatedTasksForSelectedDay() {
        Task first = taskRepository.create(openTask("First", LocalDateTime.of(2026, 4, 6, 9, 0)));
        Task second = taskRepository.create(openTask("Second", LocalDateTime.of(2026, 4, 6, 15, 0)));
        Task backlog = taskRepository.create(openTask("Backlog", null));
        Task otherDay = taskRepository.create(openTask("Other day", LocalDateTime.of(2026, 4, 7, 10, 0)));
        Task done = taskRepository.create(openTask("Done", LocalDateTime.of(2026, 4, 6, 18, 0)));
        taskRepository.markDone(done.id(), LocalDateTime.of(2026, 4, 5, 20, 40));

        int movedCount = taskRepository.bulkMoveOpenDatedTasksToNextDay(
                userId,
                LocalDate.of(2026, 4, 6),
                LocalDateTime.of(2026, 4, 5, 20, 45)
        );

        assertEquals(2, movedCount);
        assertEquals(LocalDateTime.of(2026, 4, 7, 9, 0), taskRepository.findById(first.id()).orElseThrow().plannedForAt());
        assertEquals(LocalDateTime.of(2026, 4, 7, 15, 0), taskRepository.findById(second.id()).orElseThrow().plannedForAt());
        assertTrue(taskRepository.findById(backlog.id()).orElseThrow().isBacklog());
        assertEquals(LocalDateTime.of(2026, 4, 7, 10, 0), taskRepository.findById(otherDay.id()).orElseThrow().plannedForAt());
        assertEquals(TaskStatus.DONE, taskRepository.findById(done.id()).orElseThrow().status());
    }

    private Task openTask(String title, LocalDateTime plannedForAt) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 5, 18, 30);
        return new Task(
                null,
                userId,
                title,
                title + " description",
                TaskStatus.OPEN,
                plannedForAt,
                LocalDateTime.of(2026, 4, 10, 18, 0),
                createdAt,
                createdAt,
                null,
                null
        );
    }
}
