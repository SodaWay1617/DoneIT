package com.doneit.task.web;

import com.doneit.navigation.AppNavigationService;
import com.doneit.project.application.ProjectService;
import com.doneit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CalendarHistoryTest.FixedClockConfiguration.class)
@WithMockUser(username = "owner")
class BacklogTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    Long project, task, regular;

    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW())");
        projects.create("Family", "FAMILY", null);
        project = db.queryForObject("SELECT id FROM projects WHERE code='FAMILY'", Long.class);
        mvc.perform(post("/tasks").with(csrf()).param("projectId", project.toString()).param("title", "Backlog example")
                .param("status", "BACKLOG").param("estimateMinutes", "90").param("description", "Description must not appear")
                .param("planningType", "WANTED").param("tagNames", "Prime")).andExpect(status().is3xxRedirection());
        task = db.queryForObject("SELECT id FROM tasks WHERE title='Backlog example'", Long.class);
        mvc.perform(post("/recurring").with(csrf()).param("projectId", project.toString()).param("title", "Recurring backlog")
                .param("status", "BACKLOG").param("recurrenceType", "DAILY")).andExpect(status().is3xxRedirection());
        regular = db.queryForObject("SELECT id FROM regular_tasks WHERE title='Recurring backlog'", Long.class);
    }

    @Test void compactRowsShowMetadataAndPreserveRecurringAndPausedTasks() throws Exception {
        db.update("UPDATE tasks SET status='PAUSED',created_at=TIMESTAMP '2026-04-08 12:28' WHERE id=?", task);
        var result = mvc.perform(get("/backlog?lang=en")).andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();
        String main = html.substring(html.indexOf("<main"), html.indexOf("</main>"));
        assertThat(main).contains("Backlog example", "Recurring backlog", "#Prime", "Family", "1h 30m", "Today, 12:28",
                        "/tasks/" + task + "/start-today", "/tasks/" + task + "/reschedule", "/tasks/" + task + "/closed")
                .doesNotContain("Description must not appear", "view-filters", "How to use backlog", "/done", "/recurring/new", "role=\"checkbox\"");
        assertThat(main.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("class=\"app-icon-button inbox-create\"", "backlog-filter-dialog");
        var nav = (AppNavigationService.Navigation) result.getModelAndView().getModel().get("appNav");
        assertThat(nav.projects()).allSatisfy(p -> assertThat(p.href()).startsWith("/backlog?projectId="));
        assertThat(nav.allProjectsHref()).isEqualTo("/backlog");
        assertThat(nav.main().get(2).count()).isEqualTo(2);
    }

    @Test void projectFilterAndEmptyStateRespectVisibility() throws Exception {
        Long other = db.queryForObject("SELECT p.id FROM projects p JOIN users u ON u.id=p.owner_user_id WHERE u.login='other'", Long.class);
        mvc.perform(get("/backlog").param("projectId", other.toString())).andExpect(status().isNotFound());
        String html = mvc.perform(get("/backlog").with(user("other")).param("projectId", other.toString()).param("lang", "en"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("No backlog tasks in this project", "Reset filters").doesNotContain("Backlog example", "Recurring backlog");
    }

    @Test void schedulePreservesMetadataAndRemovesRow() throws Exception {
        String target = "/backlog?projectId=" + project;
        mvc.perform(post("/tasks/{id}/reschedule", task).with(csrf()).param("plannedDate", "2026-04-10")
                .param("plannedWithTime", "true").param("plannedForAt", "2026-04-10T13:15").param("redirectTo", target))
                .andExpect(redirectedUrl(target));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, task)).isEqualTo("TODO");
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE id=?", LocalDateTime.class, task)).isEqualTo(LocalDateTime.of(2026, 4, 10, 13, 15));
        assertThat(db.queryForObject("SELECT estimate_minutes FROM tasks WHERE id=?", Integer.class, task)).isEqualTo(90);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_tags WHERE task_id=?", Integer.class, task)).isEqualTo(1);
        assertThat(mvc.perform(get(target)).andReturn().getResponse().getContentAsString()).doesNotContain("Backlog example");
    }

    @Test void takeOnPausedUsesTodayAndCloseNeverCompletesOrDeletes() throws Exception {
        db.update("UPDATE tasks SET status='PAUSED' WHERE id=?", task);
        mvc.perform(post("/tasks/{id}/start-today", task).with(csrf()).param("redirectTo", "/backlog"))
                .andExpect(redirectedUrl("/backlog"));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, task)).isEqualTo("TODO");
        assertThat(db.queryForObject("SELECT planned_date FROM tasks WHERE id=?", LocalDate.class, task)).isEqualTo(LocalDate.of(2026, 4, 8));
        mvc.perform(post("/tasks/{id}/closed", task).with(csrf()).param("redirectTo", "/backlog"))
                .andExpect(redirectedUrl("/backlog"));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?", String.class, task)).isEqualTo("CLOSED");
        assertThat(db.queryForObject("SELECT completed_at FROM tasks WHERE id=?", LocalDateTime.class, task)).isNull();
        assertThat(db.queryForObject("SELECT status FROM regular_tasks WHERE id=?", String.class, regular)).isEqualTo("BACKLOG");
    }

    @Test void editorReturnAndCreationRetainProjectContext() throws Exception {
        String target = "/backlog?projectId=" + project;
        String html = mvc.perform(get("/tasks/{id}/edit", task).param("redirectTo", target))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"redirectTo\" value=\"" + target + "\"", "href=\"" + target + "\"");
        mvc.perform(post("/tasks/{id}", task).with(csrf()).param("title", "Edited backlog")
                .param("status", "BACKLOG").param("projectId", project.toString()).param("estimateMinutes", "45").param("redirectTo", target))
                .andExpect(redirectedUrl(target));
        assertThat(mvc.perform(get(target)).andReturn().getResponse().getContentAsString()).contains("Edited backlog", "45 min");
        var form = (TaskUpsertForm) mvc.perform(get("/tasks/new").param("backlog", "true").param("projectId", project.toString()))
                .andReturn().getModelAndView().getModel().get("form");
        assertThat(form.getStatus().name()).isEqualTo("BACKLOG");
        assertThat(form.getProjectId()).isEqualTo(project);
        mvc.perform(get("/tasks/{id}/edit", task).param("redirectTo", "//evil.example/backlog"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"//evil.example/backlog\""))));
    }

    @Test void mutationsRequireCsrfAndTaskAccess() throws Exception {
        for (String action : new String[]{"start-today", "reschedule", "closed"}) {
            mvc.perform(post("/tasks/" + task + "/" + action).param("plannedDate", "2026-04-10")).andExpect(status().isForbidden());
            mvc.perform(post("/tasks/" + task + "/" + action).with(user("other")).with(csrf()).param("plannedDate", "2026-04-10"))
                    .andExpect(status().isNotFound());
        }
    }
}
