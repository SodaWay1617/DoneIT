package com.doneit.task.web;

import com.doneit.navigation.AppNavigationService;
import com.doneit.project.application.ProjectService;
import com.doneit.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
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
@WithMockUser(username="owner")
class InboxTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    Long project, task, regular;

    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE users CASCADE");
        db.update("INSERT INTO users(login,password_hash,created_at,updated_at) VALUES('owner','hash',NOW(),NOW()),('other','hash',NOW(),NOW())");
        projects.create("Family", "FAMILY", null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='FAMILY'",Long.class);
        mvc.perform(post("/tasks").with(csrf()).param("projectId",project.toString()).param("title","Incoming task")
                .param("status","NEW").param("planningType","OPTIONAL").param("tagNames","Prime"))
                .andExpect(status().is3xxRedirection());
        task=db.queryForObject("SELECT id FROM tasks WHERE title='Incoming task'",Long.class);
        mvc.perform(post("/recurring").with(csrf()).param("projectId",project.toString()).param("title","Recurring inbox excluded")
                .param("status","NEW").param("recurrenceType","DAILY")).andExpect(status().is3xxRedirection());
        regular=db.queryForObject("SELECT id FROM regular_tasks WHERE title='Recurring inbox excluded'",Long.class);
    }

    @Test void rowsHaveTagsAndCreationDateButNoLocalFiltersCheckboxesOrRecurringTasks() throws Exception {
        db.update("UPDATE tasks SET created_at=TIMESTAMP '2026-04-08 12:28:00' WHERE id=?",task);
        var result=mvc.perform(get("/inbox?lang=en&planningType=REQUIRED")).andExpect(status().isOk()).andReturn();
        String html=result.getResponse().getContentAsString();
        String main=html.substring(html.indexOf("<main"),html.indexOf("</main>"));
        assertThat(main).contains("Incoming task","#Prime","Family","Today, 12:28","data-lucide=\"x\"",
                "/tasks/"+task+"/start-today", "/tasks/"+task+"/backlog", "/tasks/"+task+"/closed")
                .doesNotContain("Recurring inbox excluded","view-filters","planningType","role=\"checkbox\"","NEW</span>","/done");
        assertThat(main.split("type=\"submit\"",-1)).hasSize(4);
        assertThat(main.split("<h1",-1)).hasSize(2);
        var nav=(AppNavigationService.Navigation)result.getModelAndView().getModel().get("appNav");
        assertThat(nav.main().get(1).count()).isEqualTo(1);
        assertThat(nav.projects()).allSatisfy(p->assertThat(p.href()).startsWith("/inbox?projectId="));
        assertThat(nav.allProjectsHref()).isEqualTo("/inbox");
        assertThat(html).contains("class=\"app-icon-button inbox-create\"");
        mvc.perform(get("/inbox?lang=ru")).andExpect(content().string(org.hamcrest.Matchers.containsString("В бэклог")));
    }

    @Test void startingUsesApplicationTodayAndPreservesTimeAndOptionalFields() throws Exception {
        db.update("UPDATE tasks SET planned_for_at=TIMESTAMP '2026-04-01 13:15:00',end_time=TIME '14:00',"
                +"deadline_at=TIMESTAMP '2026-04-20 18:00:00',estimate_minutes=45,priority='HIGH' WHERE id=?",task);
        mvc.perform(post("/tasks/{id}/start-today",task).with(csrf()).param("redirectTo","/inbox?projectId="+project))
                .andExpect(redirectedUrl("/inbox?projectId="+project));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,task)).isEqualTo("TODO");
        assertThat(db.queryForObject("SELECT planned_for_at FROM tasks WHERE id=?",LocalDateTime.class,task)).isEqualTo(LocalDateTime.of(2026,4,8,13,15));
        assertThat(db.queryForObject("SELECT end_time::text FROM tasks WHERE id=?",String.class,task)).isEqualTo("14:00:00");
        assertThat(db.queryForObject("SELECT estimate_minutes FROM tasks WHERE id=?",Integer.class,task)).isEqualTo(45);
        assertThat(db.queryForObject("SELECT deadline_at FROM tasks WHERE id=?",LocalDateTime.class,task)).isEqualTo(LocalDateTime.of(2026,4,20,18,0));
        assertThat(db.queryForObject("SELECT planning_type FROM tasks WHERE id=?",String.class,task)).isEqualTo("OPTIONAL");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_tags WHERE task_id=?",Integer.class,task)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT status FROM regular_tasks WHERE id=?",String.class,regular)).isEqualTo("NEW");
    }

    @Test void startingUndatedDoesNotInventTimeEstimateOrDeadline() throws Exception {
        mvc.perform(post("/tasks/{id}/start-today",task).with(csrf())).andExpect(redirectedUrl("/inbox"));
        var data=db.queryForMap("SELECT planned_for_at,planned_date,deadline_at,estimate_minutes FROM tasks WHERE id=?",task);
        assertThat(data.get("planned_date").toString()).isEqualTo("2026-04-08");
        assertThat(data.get("planned_for_at")).isNull();
        assertThat(data.get("deadline_at")).isNull();
        assertThat(data.get("estimate_minutes")).isNull();
    }

    @Test void backlogAndCloseUpdateCountersWithoutDeletingOrChangingTags() throws Exception {
        mvc.perform(post("/tasks/{id}/backlog",task).with(csrf()).param("redirectTo","/inbox")).andExpect(redirectedUrl("/inbox"));
        var nav=(AppNavigationService.Navigation)mvc.perform(get("/inbox")).andReturn().getModelAndView().getModel().get("appNav");
        assertThat(nav.main().get(1).count()).isZero();
        assertThat(nav.main().get(2).count()).isEqualTo(1);
        mvc.perform(post("/tasks/{id}/closed",task).with(csrf()).param("redirectTo","/inbox")).andExpect(redirectedUrl("/inbox"));
        assertThat(db.queryForObject("SELECT status FROM tasks WHERE id=?",String.class,task)).isEqualTo("CLOSED");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_tags WHERE task_id=?",Integer.class,task)).isEqualTo(1);
        mvc.perform(get("/inbox?lang=en")).andExpect(content().string(org.hamcrest.Matchers.containsString("All new tasks have been sorted")));
    }

    @Test void visibilityAndCsrfProtectEveryAction() throws Exception {
        Long otherProject=db.queryForObject("SELECT p.id FROM projects p JOIN users u ON u.id=p.owner_user_id WHERE u.login='other'",Long.class);
        mvc.perform(get("/inbox").param("projectId",otherProject.toString())).andExpect(status().isNotFound());
        for(String action:List.of("start-today","backlog","closed")) {
            mvc.perform(post("/tasks/"+task+"/"+action)).andExpect(status().isForbidden());
            mvc.perform(post("/tasks/"+task+"/"+action).with(user("other")).with(csrf())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/inbox").with(user("other"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Incoming task"))));
    }
}
