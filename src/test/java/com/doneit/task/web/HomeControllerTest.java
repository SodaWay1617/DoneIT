package com.doneit.task.web;

import com.doneit.support.IntegrationTestSupport;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class HomeControllerTest extends IntegrationTestSupport {

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-04-08T09:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long taskId;
    private Long overdueTaskId;
    private Long backlogTaskId;
    private Long tomorrowTaskId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");

        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (login, password_hash, display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "doneit",
                "hash",
                "DoneIt",
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0)
        );
        Long projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE owner_user_id = ? AND default_project = TRUE",
                Long.class,
                userId
        );

        taskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at, project_id, task_number, task_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Today task",
                "Visible on the daily page",
                "IN_PROGRESS",
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 18, 0),
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null,
                projectId,
                1L,
                "MAIN-doneit-1___doneit"
        );

        overdueTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at, project_id, task_number, task_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Overdue planned task",
                "Should stay visible on today",
                "IN_PROGRESS",
                LocalDateTime.of(2026, 4, 7, 10, 0),
                null,
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null,
                projectId,
                2L,
                "MAIN-doneit-2___doneit"
        );

        tomorrowTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at, project_id, task_number, task_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Tomorrow task",
                "Visible on selected date page",
                "IN_PROGRESS",
                LocalDateTime.of(2026, 4, 9, 11, 0),
                null,
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null,
                projectId,
                3L,
                "MAIN-doneit-3___doneit"
        );

        backlogTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at, project_id, task_number, task_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Backlog reminder",
                "Undated reminder",
                "BACKLOG",
                null,
                null,
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null,
                projectId,
                4L,
                "MAIN-doneit-4___doneit"
        );

        jdbcTemplate.update("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at, project_id, task_number, task_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "Already done",
                "Completed item",
                "DONE",
                LocalDateTime.of(2026, 4, 8, 8, 0),
                null,
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 8, 12, 0),
                LocalDateTime.of(2026, 4, 8, 12, 0),
                null,
                projectId,
                5L,
                "MAIN-doneit-5___doneit"
        );
        jdbcTemplate.update("UPDATE projects SET next_task_number = 6 WHERE id = ?", projectId);
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderTodayPageWithSections() throws Exception {
        mockMvc.perform(get("/").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today task")))
                .andExpect(content().string(containsString("Overdue planned task")))
                .andExpect(content().string(containsString("Backlog reminder")))
                .andExpect(content().string(containsString("Already done")))
                .andExpect(content().string(containsString("name=\"date\"")))
                .andExpect(content().string(containsString("action=\"/tasks/bulk-move-overdue-to-today\"")))
                .andExpect(content().string(containsString("action=\"/tasks/random-today\"")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderSelectedDatePage() throws Exception {
        mockMvc.perform(get("/tasks").param("date", "2026-04-09").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tomorrow task")))
                .andExpect(content().string(not(containsString("Today task"))))
                .andExpect(content().string(not(containsString("Overdue planned task"))))
                .andExpect(content().string(containsString("value=\"2026-04-09\"")))
                .andExpect(content().string(containsString("value=\"/tasks?date=2026-04-09\"")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderSelectedCurrentDatePageWithOverdueTasks() throws Exception {
        mockMvc.perform(get("/tasks").param("date", "2026-04-08").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today task")))
                .andExpect(content().string(containsString("Overdue planned task")))
                .andExpect(content().string(not(containsString("Tomorrow task"))));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderBacklogPage() throws Exception {
        mockMvc.perform(get("/backlog").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Backlog")))
                .andExpect(content().string(containsString("Backlog reminder")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderCreateTaskPage() throws Exception {
        mockMvc.perform(get("/tasks/new").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create Task")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderEditTaskPage() throws Exception {
        mockMvc.perform(get("/tasks/{taskId}/edit", taskId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Task")))
                .andExpect(content().string(containsString("Today task")))
                .andExpect(content().string(containsString("/tasks/" + taskId)));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldCreateDatedTaskFromForm() throws Exception {
        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .param("title", "Write architecture note")
                        .param("description", "Summarize the current module boundaries")
                        .param("status", "IN_PROGRESS")
                        .param("plannedWithTime", "true")
                        .param("plannedForAt", "2026-04-08T14:30")
                        .param("deadlineAt", "2026-04-08T18:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        Integer taskCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tasks
                WHERE title = ?
                  AND description = ?
                  AND planned_for_at = ?
                  AND deadline_at = ?
                  AND status = 'IN_PROGRESS'
                """,
                Integer.class,
                "Write architecture note",
                "Summarize the current module boundaries",
                LocalDateTime.of(2026, 4, 8, 14, 30),
                LocalDateTime.of(2026, 4, 8, 18, 0)
        );

        Assertions.assertThat(taskCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldKeepOnlyDateWhenMobileFormSubmitsBothPlanningValues() throws Exception {
        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .param("title", "Mobile planning")
                        .param("description", "Both controls were submitted")
                        .param("status", "IN_PROGRESS")
                        .param("plannedDate", "2026-04-08")
                        .param("plannedForAt", "2026-04-08T14:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        var task = jdbcTemplate.queryForMap("""
                SELECT planned_date, planned_for_at
                FROM tasks
                WHERE title = ?
                """, "Mobile planning");

        Assertions.assertThat(task.get("planned_date")).isEqualTo(java.sql.Date.valueOf("2026-04-08"));
        Assertions.assertThat(task.get("planned_for_at")).isNull();
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldCreateRecurringTaskStartingToday() throws Exception {
        Long projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE default_project = TRUE",
                Long.class
        );

        mockMvc.perform(post("/recurring")
                        .with(csrf())
                        .param("title", "Weekly review")
                        .param("projectId", projectId.toString())
                        .param("status", "IN_PROGRESS")
                        .param("priority", "NORMAL")
                        .param("recurrenceType", "WEEKLY")
                        .param("repeatInterval", "2")
                        .param("anchorDate", "2026-04-08"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        LocalDate anchorDate = jdbcTemplate.queryForObject(
                "SELECT anchor_date FROM regular_tasks WHERE title = ? AND repeat_interval = 2",
                LocalDate.class,
                "Weekly review"
        );
        Assertions.assertThat(anchorDate).isEqualTo(LocalDate.of(2026, 4, 8));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldCreateBacklogTaskWithoutPlannedDatetime() throws Exception {
        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .param("title", "Maybe later")
                        .param("description", "Good idea for future cleanup")
                        .param("status", "BACKLOG"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/backlog"));

        Integer taskCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tasks
                WHERE title = ?
                  AND description = ?
                  AND planned_for_at IS NULL
                  AND deadline_at IS NULL
                  AND status = 'BACKLOG'
                """,
                Integer.class,
                "Maybe later",
                "Good idea for future cleanup"
        );

        Assertions.assertThat(taskCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldShowValidationErrorsWhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .param("title", " ")
                        .param("description", "Still has no valid title")
                        .param("status", "IN_PROGRESS")
                        .param("plannedForAt", "2026-04-08T14:30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Still has no valid title")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldEditAllTaskFields() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}", taskId)
                        .with(csrf())
                        .param("title", "Updated task")
                        .param("description", "Updated description")
                        .param("status", "IN_PROGRESS")
                        .param("plannedWithTime", "true")
                        .param("plannedForAt", "2026-04-09T16:45")
                        .param("deadlineAt", "2026-04-10T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks?date=2026-04-09"));

        var task = jdbcTemplate.queryForMap("""
                SELECT title, description, planned_for_at, deadline_at
                FROM tasks
                WHERE id = ?
                """, taskId);

        Assertions.assertThat(task.get("title")).isEqualTo("Updated task");
        Assertions.assertThat(task.get("description")).isEqualTo("Updated description");
        Assertions.assertThat(((java.sql.Timestamp) task.get("planned_for_at")).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 9, 16, 45));
        Assertions.assertThat(((java.sql.Timestamp) task.get("deadline_at")).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 10, 10, 0));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldMoveTaskIntoBacklogWhenPlannedDatetimeIsCleared() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}", taskId)
                        .with(csrf())
                        .param("title", "Backlog now")
                        .param("description", "Moved out of the dated list")
                        .param("status", "BACKLOG"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/backlog"));

        var task = jdbcTemplate.queryForMap("""
                SELECT title, description, planned_for_at, deadline_at
                FROM tasks
                WHERE id = ?
                """, taskId);

        Assertions.assertThat(task.get("title")).isEqualTo("Backlog now");
        Assertions.assertThat(task.get("description")).isEqualTo("Moved out of the dated list");
        Assertions.assertThat(task.get("planned_for_at")).isNull();
        Assertions.assertThat(task.get("deadline_at")).isNull();
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldMarkTaskAsDoneAndMoveItOutOfActiveList() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}/done", taskId)
                        .with(csrf())
                        .param("redirectTo", "/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        var task = jdbcTemplate.queryForMap("""
                SELECT status, completed_at
                FROM tasks
                WHERE id = ?
                """, taskId);

        Assertions.assertThat(task.get("status")).isEqualTo("DONE");
        Assertions.assertThat(task.get("completed_at")).isNotNull();

        mockMvc.perform(get("/").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today task")));

        mockMvc.perform(post("/tasks/{taskId}/done", taskId)
                        .with(csrf())
                        .param("redirectTo", "/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldMarkBacklogTaskAsClosedAndKeepItOutOfActiveSections() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}/closed", backlogTaskId)
                        .with(csrf())
                        .param("redirectTo", "/backlog"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/backlog"));

        var task = jdbcTemplate.queryForMap("""
                SELECT status, closed_at
                FROM tasks
                WHERE id = ?
                """, backlogTaskId);

        Assertions.assertThat(task.get("status")).isEqualTo("CLOSED");
        Assertions.assertThat(task.get("closed_at")).isNotNull();

        mockMvc.perform(get("/").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Backlog reminder")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRescheduleOneTaskToAnotherDatetime() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}/reschedule", taskId)
                        .with(csrf())
                        .param("plannedForAt", "2026-04-10T09:15")
                        .param("redirectTo", "/tasks?date=2026-04-10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks?date=2026-04-10"));

        LocalDateTime plannedForAt = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, taskId);

        Assertions.assertThat(plannedForAt).isEqualTo(LocalDateTime.of(2026, 4, 10, 9, 15));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldMoveTaskToBacklogThroughDedicatedAction() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}/backlog", taskId)
                        .with(csrf())
                        .param("redirectTo", "/backlog"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/backlog"));

        var task = jdbcTemplate.queryForMap("""
                SELECT planned_for_at, status
                FROM tasks
                WHERE id = ?
                """, taskId);

        Assertions.assertThat(task.get("planned_for_at")).isNull();
        Assertions.assertThat(task.get("status")).isEqualTo("BACKLOG");

        mockMvc.perform(get("/backlog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today task")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldPickRandomTaskFromToday() throws Exception {
        jdbcTemplate.update("UPDATE tasks SET status = 'DONE', completed_at = ?, updated_at = ? WHERE title = ?",
                LocalDateTime.of(2026, 4, 8, 13, 0),
                LocalDateTime.of(2026, 4, 8, 13, 0),
                "Overdue planned task"
        );

        mockMvc.perform(post("/tasks/random-today").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("randomTaskTitle", "Today task"));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldMoveOverdueTasksToTodayWithoutTouchingTodayOrFutureTasks() throws Exception {
        mockMvc.perform(post("/tasks/bulk-move-overdue-to-today").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("flashMessage", "Moved 1 overdue tasks to today."));

        LocalDateTime movedOverdueTaskDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, overdueTaskId);
        LocalDateTime todayTaskDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, taskId);
        LocalDateTime tomorrowTaskDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, tomorrowTaskId);

        Assertions.assertThat(movedOverdueTaskDate).isEqualTo(LocalDateTime.of(2026, 4, 8, 10, 0));
        Assertions.assertThat(todayTaskDate).isEqualTo(LocalDateTime.of(2026, 4, 8, 10, 0));
        Assertions.assertThat(tomorrowTaskDate).isEqualTo(LocalDateTime.of(2026, 4, 9, 11, 0));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldBulkMoveUnfinishedTasksToTomorrowWithoutTouchingBacklogOrCompleted() throws Exception {
        mockMvc.perform(post("/tasks/bulk-move-to-tomorrow")
                        .with(csrf())
                        .param("date", "2026-04-08")
                        .param("redirectTo", "/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        LocalDateTime movedTaskDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, taskId);
        LocalDateTime movedOverdueTaskDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, overdueTaskId);
        LocalDateTime movedTomorrowTaskDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, LocalDateTime.class, tomorrowTaskId);
        Object backlogDate = jdbcTemplate.queryForObject("""
                SELECT planned_for_at
                FROM tasks
                WHERE id = ?
                """, Object.class, backlogTaskId);
        String doneStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM tasks
                WHERE title = 'Already done'
                """, String.class);

        Assertions.assertThat(movedTaskDate).isEqualTo(LocalDateTime.of(2026, 4, 9, 10, 0));
        Assertions.assertThat(movedOverdueTaskDate).isEqualTo(LocalDateTime.of(2026, 4, 8, 10, 0));
        Assertions.assertThat(movedTomorrowTaskDate).isEqualTo(LocalDateTime.of(2026, 4, 9, 11, 0));
        Assertions.assertThat(backlogDate).isNull();
        Assertions.assertThat(doneStatus).isEqualTo("DONE");
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldShowValidationErrorsOnEditWhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}", taskId)
                        .with(csrf())
                        .param("title", " ")
                        .param("description", "Still invalid")
                        .param("status", "IN_PROGRESS")
                        .param("plannedWithTime", "true")
                        .param("plannedForAt", "2026-04-09T16:45"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Task")))
                .andExpect(content().string(containsString("Title is required")))
                .andExpect(content().string(containsString("/tasks/" + taskId)));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldHighlightOverdueTasksAndKeepFinishedWorkOutOfMainFlow() throws Exception {
        jdbcTemplate.update("UPDATE tasks SET deadline_at = ? WHERE id = ?", LocalDateTime.of(2026, 4, 7, 23, 0), taskId);

        mockMvc.perform(get("/").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today task")))
                .andExpect(content().string(containsString("Overdue planned task")))
                .andExpect(content().string(containsString("Already done")));
    }
}
