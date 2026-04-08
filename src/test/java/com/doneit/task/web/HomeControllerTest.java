package com.doneit.task.web;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long taskId;
    private Long backlogTaskId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM users");

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

        taskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Today task",
                "Visible on the daily page",
                "OPEN",
                LocalDateTime.of(2026, 4, 8, 10, 0),
                LocalDateTime.of(2026, 4, 8, 18, 0),
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null
        );

        jdbcTemplate.update("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "Tomorrow task",
                "Visible on selected date page",
                "OPEN",
                LocalDateTime.of(2026, 4, 9, 11, 0),
                null,
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null
        );

        backlogTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "Backlog reminder",
                "Undated reminder",
                "OPEN",
                null,
                null,
                userId,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0),
                null,
                null
        );

        jdbcTemplate.update("""
                INSERT INTO tasks (title, description, status, planned_for_at, deadline_at, user_id, created_at, updated_at, completed_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                null
        );
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderTodayPageWithSections() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today view")))
                .andExpect(content().string(containsString("Backlog preview")))
                .andExpect(content().string(containsString("Completed and closed")))
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Today task')]").exists())
                .andExpect(xpath("//*[@id='backlog-preview']//*[contains(text(),'Backlog reminder')]").exists())
                .andExpect(xpath("//*[@id='completed-tasks']//*[contains(text(),'Already done')]").exists())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Backlog reminder')]").doesNotExist())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Already done')]").doesNotExist())
                .andExpect(xpath("//form[@class='date-picker']//input[@type='date' and @name='date']").exists())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'TODAY')]").exists())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'OPEN')]").exists())
                .andExpect(xpath("//*[@id='backlog-preview']//*[contains(text(),'BACKLOG')]").exists())
                .andExpect(xpath("//*[@id='completed-tasks']//*[contains(text(),'DONE')]").exists())
                .andExpect(content().string(containsString("Quick guide")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderSelectedDatePage() throws Exception {
        mockMvc.perform(get("/tasks").param("date", "2026-04-09"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selected date view")))
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Tomorrow task')]").exists())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Today task')]").doesNotExist())
                .andExpect(xpath("//input[@type='date' and @name='date']/@value").string("2026-04-09"))
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'TODAY')]").doesNotExist());
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderBacklogPage() throws Exception {
        mockMvc.perform(get("/backlog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Backlog")))
                .andExpect(content().string(containsString("Backlog reminder")))
                .andExpect(xpath("//*[contains(text(),'BACKLOG')]").exists())
                .andExpect(xpath("//*[contains(text(),'OPEN')]").exists())
                .andExpect(content().string(containsString("How to use backlog")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldRenderCreateTaskPage() throws Exception {
        mockMvc.perform(get("/tasks/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create Task")))
                .andExpect(content().string(containsString("Create task")));
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
                  AND status = 'OPEN'
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
    void shouldCreateBacklogTaskWithoutPlannedDatetime() throws Exception {
        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .param("title", "Maybe later")
                        .param("description", "Good idea for future cleanup")
                        .param("deadlineAt", "2026-04-20T09:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/backlog"));

        Integer taskCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM tasks
                WHERE title = ?
                  AND description = ?
                  AND planned_for_at IS NULL
                  AND deadline_at = ?
                  AND status = 'OPEN'
                """,
                Integer.class,
                "Maybe later",
                "Good idea for future cleanup",
                LocalDateTime.of(2026, 4, 20, 9, 0)
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
                        .param("plannedForAt", "2026-04-08T14:30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please fix the highlighted fields.")))
                .andExpect(content().string(containsString("Title is required")));
    }

    @Test
    @WithMockUser(username = "doneit")
    void shouldEditAllTaskFields() throws Exception {
        mockMvc.perform(post("/tasks/{taskId}", taskId)
                        .with(csrf())
                        .param("title", "Updated task")
                        .param("description", "Updated description")
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
                        .param("deadlineAt", "2026-04-12T12:00"))
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
        Assertions.assertThat(((java.sql.Timestamp) task.get("deadline_at")).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 12, 12, 0));
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

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Today task')]").doesNotExist())
                .andExpect(xpath("//*[@id='completed-tasks']//*[contains(text(),'Today task')]").exists());
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

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@id='backlog-preview']//*[contains(text(),'Backlog reminder')]").doesNotExist())
                .andExpect(xpath("//*[@id='completed-tasks']//*[contains(text(),'Backlog reminder')]").exists());
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
        Assertions.assertThat(task.get("status")).isEqualTo("OPEN");

        mockMvc.perform(get("/backlog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today task")));
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

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Today task')]").exists())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'OVERDUE')]").exists())
                .andExpect(xpath("//*[@id='completed-tasks']//*[contains(text(),'DONE')]").exists())
                .andExpect(xpath("//*[@id='active-tasks']//*[contains(text(),'Already done')]").doesNotExist());
    }
}
