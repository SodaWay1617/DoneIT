package com.doneit.tag.web;

import com.doneit.project.application.ProjectService;
import com.doneit.project.domain.TaskVisibilityMode;
import com.doneit.support.IntegrationTestSupport;
import com.doneit.tag.application.TagService;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username="owner")
class PersonalTagsTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ProjectService projects;
    @Autowired TagService tags;
    Long owner, katya, project, task, regular;

    @BeforeEach void setUp() throws Exception {
        db.execute("TRUNCATE TABLE users CASCADE");
        owner=addUser("owner"); katya=addUser("katya");
        projects.create("Health","HEALTH",null);
        project=db.queryForObject("SELECT id FROM projects WHERE code='HEALTH'",Long.class);
        projects.add(project,katya);
        mvc.perform(post("/tasks").with(csrf()).param("title","Healthy sleep").param("projectId",project.toString())
                .param("status","IN_PROGRESS").param("estimateMinutes","30").param("tagsPresent","true")
                .param("tagNames","Prime","#prime")).andExpect(status().is3xxRedirection());
        task=db.queryForObject("SELECT id FROM tasks WHERE title='Healthy sleep'",Long.class);
        mvc.perform(post("/recurring").with(csrf()).param("title","Healthy food").param("projectId",project.toString())
                .param("status","IN_PROGRESS").param("recurrenceType","DAILY").param("estimateMinutes","20")
                .param("tagsPresent","true").param("tagNames","Prime")).andExpect(status().is3xxRedirection());
        regular=db.queryForObject("SELECT id FROM regular_tasks WHERE title='Healthy food'",Long.class);
        as("owner");
    }

    @Test void labelsArePersonalEvenOnTheSameSharedTasks() throws Exception {
        tags.replace(task,false,List.of("Prime","Health"));
        as("katya");
        assertThat(tags.names(task,false)).isEmpty();
        tags.replace(task,false,List.of("Private"));
        tags.replace(regular,true,List.of("Private"));
        Long privateId=tags.list().getFirst().id();
        as("owner");
        assertThat(tags.names(task,false)).containsExactly("Health","Prime");
        assertThat(tags.names(regular,true)).containsExactly("Prime");
        assertThat(tags.list()).extracting(TagService.Tag::name).doesNotContain("Private");
        mvc.perform(get("/tags/{id}",privateId)).andExpect(status().isNotFound());
        mvc.perform(post("/tags/{id}",privateId).with(csrf()).param("name","Stolen")).andExpect(status().isNotFound());
        mvc.perform(post("/tags/{id}/delete",privateId).with(csrf())).andExpect(status().isNotFound());
        as("owner");
        tags.replace(task,false,List.of());
        as("katya");
        assertThat(tags.names(task,false)).containsExactly("Private");
    }

    @Test void monthlyFactUsesOwnEntriesAcrossOrdinaryAndRecurringTasks() {
        db.update("INSERT INTO task_time_entries(task_id,user_id,entry_date,minutes,created_at) VALUES(?,?,DATE '2026-10-01',45,NOW()),(?,?,DATE '2026-10-31',-5,NOW()),(?,?,DATE '2026-11-01',90,NOW()),(?,?,DATE '2026-10-03',100,NOW())",
                task,owner,task,owner,task,owner,task,katya);
        db.update("INSERT INTO regular_task_time_entries(regular_task_id,user_id,entry_date,minutes,created_at) VALUES(?,?,DATE '2026-10-05',20,NOW()),(?,?,DATE '2026-09-30',200,NOW()),(?,?,DATE '2026-10-05',200,NOW())",
                regular,owner,regular,owner,regular,katya);
        var detail=tags.detail(prime(),YearMonth.of(2026,10));
        assertThat(detail.actualMinutes()).isEqualTo(60);
        assertThat(detail.ordinaryCount()).isEqualTo(1);
        assertThat(detail.recurringCount()).isEqualTo(1);
        assertThat(detail.tasks()).extracting(TagService.Task::title).containsExactlyInAnyOrder("Healthy sleep","Healthy food");
        assertThat(tags.list()).singleElement().extracting(TagService.Tag::taskCount).isEqualTo(2L);
    }

    @Test void membershipAndTaskVisibilityStillApplyToPreviouslyTaggedTasks() {
        as("katya");
        tags.replace(task,false,List.of("Mine"));
        Long id=tags.list().getFirst().id();
        as("owner");
        projects.update(project,"Health","HEALTH",null,TaskVisibilityMode.SCHEDULE_ONLY);
        as("katya");
        assertThat(tags.detail(id,YearMonth.of(2026,10)).tasks()).isEmpty();
        assertThat(tags.list().getFirst().taskCount()).isZero();
        assertThat(tags.visible(false)).doesNotContainKey(task);
        assertThatThrownBy(()->tags.replace(task,false,List.of("No"))).isInstanceOf(ResponseStatusException.class);
        as("owner");
        projects.update(project,"Health","HEALTH",null,TaskVisibilityMode.ALL_MEMBERS);
        projects.remove(project,katya);
        as("katya");
        assertThat(tags.detail(id,YearMonth.of(2026,10)).tasks()).isEmpty();
    }

    @Test void editsReplaceOnlyExplicitlySubmittedPersonalLabels() throws Exception {
        mvc.perform(post("/tasks/{id}",task).with(csrf()).param("title","Renamed")
                .param("projectId",project.toString()).param("status","IN_PROGRESS")).andExpect(status().is3xxRedirection());
        as("owner");
        assertThat(tags.names(task,false)).containsExactly("Prime");
        mvc.perform(post("/tasks/{id}",task).with(csrf()).param("title","Renamed")
                .param("projectId",project.toString()).param("status","IN_PROGRESS")
                .param("tagsPresent","true")).andExpect(status().is3xxRedirection());
        as("owner");
        assertThat(tags.names(task,false)).isEmpty();
        mvc.perform(post("/recurring/{id}",regular).with(csrf()).param("title","Food renamed")
                .param("projectId",project.toString()).param("status","IN_PROGRESS").param("recurrenceType","DAILY")
                .param("tagsPresent","true").param("tagNames","New tag")).andExpect(status().is3xxRedirection());
        as("owner");
        assertThat(tags.names(regular,true)).containsExactly("New tag");
    }

    @Test void invalidLabelsDoNotPartiallyCreateOrEditTasks() throws Exception {
        mvc.perform(post("/tasks").with(csrf()).param("title","Invalid ordinary").param("status","NEW")
                .param("tagNames","<bad>")).andExpect(status().isOk()).andExpect(view().name("task-form"));
        mvc.perform(post("/recurring").with(csrf()).param("title","Invalid recurring").param("status","NEW")
                .param("recurrenceType","DAILY").param("tagNames","<bad>"))
                .andExpect(status().isOk()).andExpect(view().name("recurring-form"));
        mvc.perform(post("/tasks/{id}",task).with(csrf()).param("title","Do not change")
                .param("status","IN_PROGRESS").param("tagsPresent","true").param("tagNames","<bad>"))
                .andExpect(status().isOk()).andExpect(view().name("task-form"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE title='Invalid ordinary'",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM regular_tasks WHERE title='Invalid recurring'",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT title FROM tasks WHERE id=?",String.class,task)).isEqualTo("Healthy sleep");
        as("owner");
        assertThat(tags.names(task,false)).containsExactly("Prime");
    }

    @Test void renameConflictIsHandledAndDeletingTagDoesNotDeleteTasks() throws Exception {
        Long prime=prime(), another=tags.create("Another").id();
        mvc.perform(post("/tags/{id}",another).with(csrf()).param("name","#PRIME"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("tagError","tag.conflict"));
        mvc.perform(post("/tags/{id}",prime).with(csrf()).param("name","Focus")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/tags/{id}",prime).param("month","2026-10")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("#Focus")));
        mvc.perform(post("/tags/{id}/delete",prime).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM task_tags",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE id=?",Long.class,task)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM regular_tasks WHERE id=?",Long.class,regular)).isEqualTo(1);
    }

    @Test void pagesRenderBothKindsAndFormSelectionsAndEnforceCsrf() throws Exception {
        Long prime=prime();
        for(String path:List.of("/tags","/tags/"+prime+"?month=2026-10","/tasks/"+task+"/edit","/recurring/"+regular+"/edit",
                "/","/tasks?date=2026-10-24","/kanban","/calendar","/inbox","/backlog","/finished")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/tags/{id}",prime).param("month","2026-10")).andExpect(content().string(org.hamcrest.Matchers.containsString("Healthy sleep")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Healthy food")));
        mvc.perform(get("/tasks/{id}/edit",task)).andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Prime\" checked")));
        mvc.perform(post("/tags").param("name","No csrf")).andExpect(status().isForbidden());
        mvc.perform(get("/tags/{id}",prime).param("month","garbage")).andExpect(status().isBadRequest());
    }

    private Long prime(){ return tags.list().stream().filter(t->t.name().equals("Prime")).findFirst().orElseThrow().id(); }
    private Long addUser(String login) {
        return db.queryForObject("INSERT INTO users(login,password_hash,display_name,created_at,updated_at) VALUES(?,'hash',?,NOW(),NOW()) RETURNING id",
                Long.class,login,login);
    }
    private void as(String login) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(login,"password","ROLE_USER"));
    }
}
