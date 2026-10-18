package com.doneit.task.web;

import com.doneit.support.IntegrationTestSupport;
import com.doneit.task.application.TaskChangeHistoryService.History;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskChangeHistoryTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager transactions;
    Long project;

    @BeforeEach void setup() {
        db.execute("TRUNCATE users CASCADE");
        for (String login : new String[]{"owner", "katya"})
            db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES(?,'unused',NOW(),NOW())", login);
        project = db.queryForObject("SELECT id FROM projects WHERE owner_user_id=(SELECT id FROM users WHERE login='owner')", Long.class);
    }

    @Test void ordinaryChangesCaptureOldAndNewValuesWithoutNoOpDuplicates() throws Exception {
        Long id = task();
        assertThat(count()).isZero();
        db.update("UPDATE tasks SET planned_date='2026-10-20',status='IN_PROGRESS',estimate_minutes=45,deadline_at='2026-10-21 10:00' WHERE id=?", id);
        assertThat(count()).isEqualTo(4);
        db.update("UPDATE tasks SET title='Renamed',status=status,estimate_minutes=estimate_minutes WHERE id=?", id);
        assertThat(count()).isEqualTo(4);
        History history = history(id, false, "ru");
        assertThat(history.entries()).anySatisfy(entry -> {
            assertThat(entry.field()).isEqualTo("Оценка времени");
            assertThat(entry.before()).isEqualTo("30 мин");
            assertThat(entry.after()).isEqualTo("45 мин");
        });
        db.update("UPDATE tasks SET estimate_minutes=NULL,deadline_at=NULL WHERE id=?", id);
        assertThat(history(id, false, "en").entries().getFirst().after()).isEqualTo("Not set");
    }

    @Test void endpointRescheduleAndCompletionAreRecordedAndDetailsAreCollapsed() throws Exception {
        Long id = task();
        db.update("UPDATE tasks SET planned_date='2026-10-20' WHERE id=?", id);
        mvc.perform(post("/tasks/{id}/reschedule", id).with(user("owner")).with(csrf()).param("plannedDate", "2026-10-22"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks/{id}/done", id).with(user("owner")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var history = history(id, false, "en");
        assertThat(history.reschedules()).isEqualTo(1);
        assertThat(history.entries().getFirst().after()).isEqualTo("DONE");
        mvc.perform(get("/tasks/{id}/edit", id).with(user("owner"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("<details class=\"task-history\">")))
                .andExpect(content().string(not(containsString("<details open"))));
    }

    @Test void regularTasksRecordStartTimeStatusAndEstimateIndependently() throws Exception {
        mvc.perform(post("/recurring").with(user("owner")).with(csrf()).param("title", "Habit")
                .param("projectId", project.toString()).param("status", "IN_PROGRESS").param("estimateMinutes", "20"))
                .andExpect(status().is3xxRedirection());
        Long id = db.queryForObject("SELECT id FROM regular_tasks", Long.class);
        db.update("UPDATE regular_tasks SET occurrence_time='09:00',estimate_minutes=25,status='DOCUMENTATION' WHERE id=?", id);
        assertThat(history(id, true, "en").total()).isEqualTo(3);
        db.update("UPDATE regular_tasks SET occurrence_time='10:00' WHERE id=?", id);
        assertThat(history(id, true, "en").reschedules()).isEqualTo(1);
        assertThat(history(id, true, "en").entries().getFirst().after()).endsWith("10:00");
    }

    @Test void privateTaskHistoryCannotBeReadByOtherUsers() throws Exception {
        Long id = task();
        db.update("UPDATE tasks SET estimate_minutes=60 WHERE id=?", id);
        mvc.perform(get("/tasks/{id}/edit", id).with(user("katya"))).andExpect(status().isNotFound());
        assertThat(history(id, false, "en").total()).isEqualTo(1);
    }

    @Test void longHistoryIsBoundedButCountersAreNot() throws Exception {
        Long id = task();
        for (int minutes = 31; minutes <= 131; minutes++)
            db.update("UPDATE tasks SET estimate_minutes=? WHERE id=?", minutes, id);
        var history = history(id, false, "en");
        assertThat(history.total()).isEqualTo(101);
        assertThat(history.entries()).hasSize(100);
        assertThat(history.truncated()).isTrue();
        assertThat(history.entries().getFirst().after()).isEqualTo("131 min");
    }

    @Test void rollbackDoesNotLeaveHistoryAndTaskDeletionCascades() throws Exception {
        Long id = task();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            db.update("UPDATE tasks SET estimate_minutes=60 WHERE id=?", id);
            tx.setRollbackOnly();
        });
        assertThat(count()).isZero();
        db.update("UPDATE tasks SET estimate_minutes=60 WHERE id=?", id);
        assertThat(count()).isEqualTo(1);
        db.update("DELETE FROM tasks WHERE id=?", id);
        assertThat(count()).isZero();
    }

    @Test void createFormsDoNotShowHistory() throws Exception {
        for (String path : new String[]{"/tasks/new", "/recurring/new"})
            mvc.perform(get(path).with(user("owner"))).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("<details class=\"task-history\">"))));
    }

    private Long task() throws Exception {
        mvc.perform(post("/tasks").with(user("owner")).with(csrf()).param("title", "Work")
                .param("status", "TODO").param("withoutPlannedDate", "true").param("withoutDeadline", "true")
                .param("estimateMinutes", "30")).andExpect(status().is3xxRedirection());
        return db.queryForObject("SELECT id FROM tasks", Long.class);
    }

    private long count() { return db.queryForObject("SELECT COUNT(*) FROM task_change_history", Long.class); }

    private History history(Long id, boolean regular, String language) throws Exception {
        var result = mvc.perform(get(regular ? "/recurring/{id}/edit" : "/tasks/{id}/edit", id)
                        .with(user("owner")).param("lang", language)).andExpect(status().isOk()).andReturn();
        return (History) result.getModelAndView().getModel().get("changeHistory");
    }
}
